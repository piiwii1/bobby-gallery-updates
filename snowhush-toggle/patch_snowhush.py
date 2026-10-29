#!/usr/bin/env python3
"""Snowhush v0.5.6 -> PiiWii switch 0.5.7.
One click on the tray snowflake toggles all snow overlays; right-click opens settings.
Licensed original source: MIT, https://github.com/mbezruchko/Snowhush
"""
from pathlib import Path
import sys

base=Path(sys.argv[1]) if len(sys.argv)>1 else Path("snowhush")

def edit(path, changes):
    p=base/path
    data=p.read_text(encoding="utf-8")
    for old,new in changes:
        n=data.count(old)
        if n!=1:
            raise SystemExit(f"PATCH FAILED: {path}: expected one match, found {n} for {old[:100]!r}")
        data=data.replace(old,new,1)
    p.write_text(data,encoding="utf-8")
    print(f"patched {path}")

edit("windows/Cargo.toml",[
    ('version = "0.5.6"', 'version = "0.5.7"'),
])
edit("windows/src/prefs.rs",[
    ('    pub snowfall: Snowfall,\n', '    pub snowfall: Snowfall,\n    pub enabled: bool,\n'),
    ('            snowfall: snowfall.unwrap_or(if calm { Snowfall::Light } else { Snowfall::Steady }),\n',
     '            snowfall: snowfall.unwrap_or(if calm { Snowfall::Light } else { Snowfall::Steady }),\n            enabled: read(w!("enabled")).is_none_or(|v| v != 0),\n'),
    ('        write(w!("snowfall"), snowfall as u32);\n',
     '        write(w!("snowfall"), snowfall as u32);\n        write(w!("enabled"), self.enabled as u32);\n'),
])
edit("windows/src/screen.rs",[
    ('    covered: bool,\n', '    covered: bool,\n    enabled: bool,\n'),
    ('            covered: false,\n', '            covered: false,\n            enabled: true,\n'),
    ('    /// Hides the snow while a full-screen app covers the display, and stops it, as it stops over a full-screen app on a Mac.\n',
     '''    /// A taskbar switch hides every overlay without changing snowfall preferences or the snow pile.
    /// The simulation is paused by the caller; enabling again resumes at the next frame.
    pub fn set_enabled(&mut self, enabled: bool) {
        if self.enabled == enabled {
            return;
        }
        self.enabled = enabled;
        unsafe {
            let _ = ShowWindow(self.overlay.0, if enabled && !self.covered { SW_SHOWNOACTIVATE } else { SW_HIDE });
        }
        if enabled {
            self.resume();
        }
    }

    /// Hides the snow while a full-screen app covers the display, and stops it, as it stops over a full-screen app on a Mac.
'''),
    ('if covered { SW_HIDE } else { SW_SHOWNOACTIVATE }',
     'if covered || !self.enabled { SW_HIDE } else { SW_SHOWNOACTIVATE }'),
])
edit("windows/src/snow.rs",[
    ('pub enum Command {\n', 'pub enum Command {\n    Enabled(bool),\n'),
    ('            match command {\n                Command::Quit => return Ok(()),',
     '''            match command {
                Command::Quit => return Ok(()),
                Command::Enabled(on) => {
                    settings.enabled = on;
                    screens.iter_mut().for_each(|screen| screen.set_enabled(on));
                    log!("snow switch: {}", if on { "ON" } else { "OFF" });
                },'''),
    ('        if active {\n', '        if active && settings.enabled {\n'),
    ('        let due = screens.iter().map(|screen| screen.next_due).min().filter(|_| active);\n',
     '        let due = screens.iter().map(|screen| screen.next_due).min().filter(|_| active && settings.enabled);\n'),
    ('        screen.simulation.walls = walls(&screen.monitor, &displays);\n',
     '        screen.simulation.walls = walls(&screen.monitor, &displays);\n        screen.set_enabled(settings.enabled);\n'),
])
edit("windows/src/app.rs",[
    ('const ID_CLEAR: u32 = 207;\n', 'const ID_CLEAR: u32 = 207;\nconst ID_ENABLED: u32 = 208;\n'),
    ('        add_tray_icon(window, icon);\n', '        add_tray_icon(window, icon, settings.enabled);\n'),
    ('            if event == WM_CONTEXTMENU || event == NIN_SELECT || event == NIN_KEYSELECT {\n',
     '''            if event == NIN_SELECT || event == NIN_KEYSELECT {
                toggle_snow(window);
            } else if event == WM_CONTEXTMENU {
'''),
    ('                    add_tray_icon(window, app.icon);\n', '                    add_tray_icon(window, app.icon, app.settings.enabled);\n'),
    ('fn show_menu(window: HWND, x: i32, y: i32) {\n',
     '''/// Left click on the tray icon instantly switches the complete snow effect ON/OFF.
fn toggle_snow(window: HWND) {
    let enabled = APP.with_borrow_mut(|app| {
        let app = app.as_mut()?;
        app.settings.enabled = !app.settings.enabled;
        app.settings.save();
        let enabled = app.settings.enabled;
        send(app, Command::Enabled(enabled));
        Some(enabled)
    });
    if let Some(enabled) = enabled {
        update_tray_tip(window, enabled);
        log!("tray snow toggle: {}", if enabled { "ON" } else { "OFF" });
    }
}

fn show_menu(window: HWND, x: i32, y: i32) {
'''),
    ('        let _ = AppendMenuW(menu, MF_POPUP, snowfall.0 as usize, w!("Snowfall"));\n',
     '''        let _ = AppendMenuW(menu, MF_STRING | checked(settings.enabled), ID_ENABLED as usize, w!("Snow ON / OFF"));
        let _ = AppendMenuW(menu, MF_SEPARATOR, 0, PCWSTR::null());
        let _ = AppendMenuW(menu, MF_POPUP, snowfall.0 as usize, w!("Snowfall"));
'''),
    ('        ID_QUIT => quit(window),\n', '        ID_QUIT => quit(window),\n        ID_ENABLED => toggle_snow(window),\n'),
    ('fn add_tray_icon(window: HWND, icon: HICON) {\n', 'fn add_tray_icon(window: HWND, icon: HICON, enabled: bool) {\n'),
    ('        let _ = Shell_NotifyIconW(NIM_SETVERSION, &data);\n    }\n}\n\nfn remove_tray_icon',
     '''        let _ = Shell_NotifyIconW(NIM_SETVERSION, &data);
    }
    update_tray_tip(window, enabled);
}

/// The hover text makes the current state explicit and tells users how to toggle.
fn update_tray_tip(window: HWND, enabled: bool) {
    let tip = if enabled { "Snowhush — ON | Click: OFF" } else { "Snowhush — OFF | Click: ON" };
    let mut data = NOTIFYICONDATAW {
        cbSize: size_of::<NOTIFYICONDATAW>() as u32,
        hWnd: window,
        uID: 1,
        uFlags: NIF_TIP | NIF_SHOWTIP,
        ..Default::default()
    };
    for (slot, unit) in data.szTip.iter_mut().zip(tip.encode_utf16()) {
        *slot = unit;
    }
    unsafe {
        let _ = Shell_NotifyIconW(NIM_MODIFY, &data);
    }
}

fn remove_tray_icon'''),
])
print("Snowhush PiiWii 0.5.7 switch patch completed.")

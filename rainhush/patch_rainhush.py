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



# PiiWii Rainhush 0.5.8 — independent application with realistic rain.
import struct, zlib
def rain_edit(name, pairs):
    p = base / name
    data = p.read_text(encoding='utf8')
    for old,new in pairs:
        count = data.count(old)
        if count != 1:
            raise RuntimeError(f"Rain patch {name}: {count} occurrences of {old[:70]!r}")
        data = data.replace(old,new,1)
    p.write_text(data,encoding='utf8')
    print("RAIN:", name)
rain_edit("windows/Cargo.toml",[('version = "0.5.7"','version = "0.5.8"')])
rain_edit("windows/src/prefs.rs",[
 ('accumulate: read(w!("accumulate")).is_none_or(|v| v != 0)', 'accumulate: false'),
 ('snow_on_windows: read(w!("snowOnWindows")).is_none_or(|v| v != 0)', 'snow_on_windows: false'),
 ('welcomed: read(w!("welcomed")).is_some_and(|v| v != 0)', 'welcomed: true'),
])
rain_edit("windows/src/simulation.rs",[
 ('Snowfall::Light => "Light",','Snowfall::Light => "Pluie légère",'),
 ('Snowfall::Steady => "Steady",','Snowfall::Steady => "Pluie normale",'),
 ('Snowfall::Heavy => "Heavy",','Snowfall::Heavy => "Forte pluie",'),
 ('Snowfall::Variable => "Variable",','Snowfall::Variable => "Pluie variable",'),
 ('Snowfall::Light => 150.0,','Snowfall::Light => 480.0,'),
 ('Snowfall::Steady => 380.0,','Snowfall::Steady => 1150.0,'),
 ('Snowfall::Heavy => 800.0,','Snowfall::Heavy => 2400.0,'),
 ('Snowfall::Variable => 380.0,','Snowfall::Variable => 1150.0,'),
 ('accumulates: true,','accumulates: false,'),
 ('flake.velocity.x += (wind * (0.35 + 0.65 * flake.depth) - flake.velocity.x) * follow;',
  'flake.velocity.x += (wind * (1.5 + 1.5 * flake.depth) - flake.velocity.x) * follow;'),
 ('flake.position.x += (flake.velocity.x + sway) * dt;\n            flake.position.y += flake.velocity.y * flutter * dt;',
  'flake.position.x += flake.velocity.x * dt;\n            flake.position.y += flake.velocity.y * dt;'),
 ('let fall_speed = if near { random.range(75.0, 100.0) } else { 16.0 + depth * 50.0 + random.range(0.0, 10.0) };',
  'let fall_speed = if near { random.range(800.0, 1130.0) } else { 480.0 + depth * 360.0 + random.range(0.0, 140.0) };'),
 ('velocity: V2::new(wind * (0.35 + 0.65 * depth), -fall_speed),',
  'velocity: V2::new(wind * (1.5 + 1.5 * depth), -fall_speed),'),
 ('size: if near { random.range(8.0, 13.0) } else { 1.4 + depth * 4.4 + random.range(0.0, 1.0) },',
  'size: if near { random.range(1.35, 2.1) } else { 0.65 + depth * 0.9 + random.range(0.0, 0.35) },'),
 ('alpha: if near { 0.28 } else { 0.4 + depth * 0.5 },',
  'alpha: if near { 0.66 } else { 0.28 + depth * 0.37 },'),
 ('soft: if near { 0.95 } else { 0.75 - depth * 0.45 },','soft: if near { 0.1 } else { 0.18 },'),
 ('sway_amount: if near { random.range(10.0, 20.0) } else { random.range(4.0, 9.0) + depth * 10.0 },','sway_amount: 0.0,'),
 ('settles: !near,','settles: false,'),
 ('mass: 1.0,\n            tint: [1.0; 3],','mass: 1.0,\n            tint: [0.7, 0.82, 1.0],'),
])
rain_edit("windows/src/shaders.hlsl",[
 ('// About a 1/55 s exposure, capped so even the fastest grain stays a short smear and never reads as rain.\n    float2 travel = s.velocity * 0.018;\n    float smear = min(length(travel), s.size * (s.lump > 0.0 ? 1.2 : 2.2));',
  '// Rain exposure: narrow velocity-aligned streaks.\n    float2 travel = s.velocity * 0.030;\n    float smear = min(length(travel), 34.0);'),
 ('float extent = max(pixels * reach + length(streak), 2.0);','float extent = max(pixels * reach + length(streak), 3.0);'),
 ('o.radius = pixels / extent * 0.5;','o.radius = max(pixels * 0.38, 0.42) / extent;'),
 ('o.alpha = s.alpha * min(pixels / 2.0, 1.0) * pixels / max(pixels + length(streak) * 0.6, 0.001);','o.alpha = saturate(s.alpha * 0.82);'),
 ('float3 core = float3(0.965, 0.975, 0.995);\n    float3 rim = float3(0.62, 0.67, 0.77);','float3 core = float3(0.78, 0.88, 1.0);\n    float3 rim = float3(0.45, 0.63, 0.82);'),
])
rain_edit("windows/src/app.rs",[
 ('SetTimer(Some(window), TIMER_UPDATES, 30_000, None);','// Independent Rainhush: upstream snow updates disabled.'),
 ('w!("Snow ON / OFF")','w!("Pluie : ON / OFF")'),
 ('w!("Snowfall")','w!("Intensité de pluie")'),
 ('        let _ = AppendMenuW(menu, MF_STRING | checked(settings.accumulate), ID_ACCUMULATE as usize, w!("Accumulate"));\n        let melt = MF_STRING | checked(settings.melt) | if settings.accumulate { MF_ENABLED } else { MF_GRAYED };\n        let _ = AppendMenuW(menu, melt, ID_MELT as usize, w!("Melt Snow"));\n        let windows = MF_STRING | checked(settings.snow_on_windows) | if settings.behind_windows { MF_GRAYED } else { MF_ENABLED };\n        let _ = AppendMenuW(menu, windows, ID_WINDOWS as usize, w!("Snow on Windows"));\n        let _ = AppendMenuW(menu, MF_STRING, ID_CLEAR as usize, w!("Clear Snow"));\n',''),
 ('        let _ = AppendMenuW(menu, MF_STRING, ID_UPDATES as usize, w!("Check for Updates…"));\n',''),
 ('w!("Behind Windows")','w!("Derrière les fenêtres")'),
 ('w!("Show in Screenshots")','w!("Visible en capture d’écran")'),
 ('w!("Open at Login")','w!("Démarrer avec Windows")'),
 ('w!("Quit")','w!("Quitter")'),
 ('"Snowhush — ON | Click: OFF"','"Rainhush — Pluie ON (clic : OFF)"'),
 ('"Snowhush — OFF | Click: ON"','"Rainhush — Pluie OFF (clic : ON)"'),
])
for p in (base/"windows/src").rglob("*.rs"):
    if p.name == "update.rs": continue
    data=p.read_text(encoding="utf8").replace("Snowhush","Rainhush").replace("SNOWHUSH","RAINHUSH")
    data=data.replace('env!("RAINHUSH_APP")','env!("SNOWHUSH_APP")')
    if p.name=="log.rs": data=data.replace("snowhush.log","rainhush.log")
    p.write_text(data,encoding="utf8")
p=base/"windows/build.rs"
s=p.read_text(encoding="utf8").replace('("ProductName", "Snowhush")','("ProductName", "Rainhush PiiWii")').replace('("InternalName", "Snowhush")','("InternalName", "Rainhush")')
p.write_text(s,encoding="utf8")
def iconpng(n):
    rows=bytearray()
    for y in range(n):
        rows.append(0)
        for x in range(n):
            a=(x+.5)/n; b=(y+.5)/n
            cloud=(((a-.33)/.19)**2+((b-.41)/.16)**2<1 or ((a-.53)/.24)**2+((b-.32)/.22)**2<1 or ((a-.72)/.16)**2+((b-.43)/.14)**2<1 or (.24<a<.80 and .39<b<.53))
            drop=any(abs(a-c)<.022 and .63<b<.83 for c in (.30,.50,.70))
            rows.extend((190,215,240,255) if cloud else ((38,150,255,255) if drop else (0,0,0,0)))
    def chunk(k,v):return struct.pack(">I",len(v))+k+v+struct.pack(">I",zlib.crc32(k+v)&0xffffffff)
    return b"\x89PNG\r\n\x1a\n"+chunk(b'IHDR',struct.pack(">IIBBBBB",n,n,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(rows),9))+chunk(b'IEND',b'')
small,large=iconpng(64),iconpng(256)
(base/"windows/assets/icon-256.png").write_bytes(large)
ico=bytearray(struct.pack("<HHH",0,1,2)); offset=38
for n,b in [(64,small),(256,large)]:
    ico.extend(struct.pack("<BBBBHHII",n%256,n%256,0,0,1,32,len(b),offset))
    offset+=len(b)
(base/"windows/assets/Snowhush.ico").write_bytes(ico+small+large)
print("Rainhush PiiWii 0.5.8 build patch complete")

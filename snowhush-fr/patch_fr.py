#!/usr/bin/env python3
# French localization and blue/grey tray switch for Snowhush PiiWii 0.5.9.
# Apply after patch_snowhush_058.py. Original Snowhush: MIT.
from pathlib import Path
import sys, math, struct, zlib
root = Path(sys.argv[1]) if len(sys.argv)>1 else Path('snowhush')

def patch(relative, pairs):
    p=root/relative
    data=p.read_text(encoding='utf-8')
    for old,new in pairs:
        n=data.count(old)
        if n!=1:
            raise SystemExit(f'Cannot patch {relative}: found {n} matches: {old[:85]!r}')
        data=data.replace(old,new,1)
    p.write_text(data,encoding='utf-8')
    print(f'FR: {relative}')

patch('windows/Cargo.toml',[('version = "0.5.8"','version = "0.5.9"')])

patch('windows/src/app.rs',[
('    icon: HICON,\n', '    icon: HICON,\n    off_icon: HICON,\n'),
('        let icon = tray_icon();\n        add_tray_icon(window, icon, settings.enabled);',
 '''        let icon = tray_icon(2); // colored switch: ON
        let off_icon = tray_icon(3); // grey switch: OFF
        add_tray_icon(window, if settings.enabled {icon} else {off_icon}, settings.enabled);'''),
('        APP.set(Some(App { settings, commands, wake, snow, icon, locked:',
 '        APP.set(Some(App { settings, commands, wake, snow, icon, off_icon, locked:'),
('                    add_tray_icon(window, app.icon, app.settings.enabled);',
 '                    add_tray_icon(window, if app.settings.enabled {app.icon} else {app.off_icon}, app.settings.enabled);'),
('        APP.set(Some(App { settings, commands, wake, snow, icon, off_icon, locked: false, display_off: false, away: false, update: None }));',
 '        APP.set(Some(App { settings, commands, wake, snow, icon, off_icon, locked: false, display_off: false, away: false, update: None }));\n        update_tray_tip(window, settings.enabled);'),
('            let title: Vec<u16> = option.title().encode_utf16().chain([0]).collect();',
 '''            let french = match i {0=>"Arrêt",1=>"Faible",2=>"Modérée",3=>"Forte",4=>"Variable",_=>option.title()};
            let title: Vec<u16> = french.encode_utf16().chain([0]).collect();'''),
('        let _ = AppendMenuW(menu, MF_STRING | checked(settings.enabled), ID_ENABLED as usize, w!("Snow ON / OFF"));',
 '''        let season_flag = if weather::in_season() {MF_ENABLED} else {MF_GRAYED};
        let switch_title = if settings.enabled {w!("Interrupteur : NEIGE ACTIVE")} else {w!("Interrupteur : NEIGE COUPÉE")};
        let _ = AppendMenuW(menu, MF_STRING | season_flag | checked(settings.enabled), ID_ENABLED as usize, switch_title);'''),
('        let _ = AppendMenuW(menu, MF_STRING | checked(settings.snow_mode == 0), ID_AUTO as usize, w!("Automatic (PiiWii Portal weather)"));',
 '        let _ = AppendMenuW(menu, MF_STRING | season_flag | checked(settings.snow_mode == 0), ID_AUTO as usize, w!("Auto : météo PiiWii Portal"));'),
('w!("Snowfall")','w!("Intensité de la neige")'),
('w!("Accumulate")','w!("Accumulation")'),
('w!("Melt Snow")','w!("Faire fondre la neige")'),
('w!("Snow on Windows")','w!("Neige sur les fenêtres")'),
('w!("Clear Snow")','w!("Effacer la neige")'),
('w!("Behind Windows")','w!("Derrière les fenêtres")'),
('w!("Show in Screenshots")','w!("Visible sur les captures d’écran")'),
('w!("Open at Login")','w!("Lancer avec Windows")'),
('w!("Check for Updates…")','w!("Mises à jour officielles…")'),
('w!("Quit")','w!("Quitter Snowhush")'),
('let text = format!("Snowhush {} is available. You have {current}.\\n\\nDownload it now?", release.version);',
 'let text = format!("La version officielle Snowhush {} est disponible. Vous avez PiiWii {current}.\\n\\nInstaller la version officielle supprimera les modifications PiiWii. Ouvrir son téléchargement ?", release.version);'),
('notify(window, &format!("Snowhush {} is available", release.version), "Click to download it.");',
 'notify(window, &format!("Version officielle Snowhush {}", release.version), "Cliquez pour ouvrir le téléchargement.");'),
('message(window, &format!("You\'re up to date.\\n\\nSnowhush {current} is the latest version."), MB_OK | MB_ICONINFORMATION);',
 'message(window, &format!("Aucune version officielle plus récente détectée.\\n\\nSnowhush PiiWii FR {current}."), MB_OK | MB_ICONINFORMATION);'),
('message(window, &format!("Couldn\'t check for updates.\\n\\n{error}"), MB_OK | MB_ICONWARNING);',
 'message(window, &format!("Impossible de vérifier les mises à jour.\\n\\n{error}"), MB_OK | MB_ICONWARNING);'),
('            let _ = DestroyIcon(app.icon);','            let _ = DestroyIcon(app.icon);\n            let _ = DestroyIcon(app.off_icon);'),
('fn tray_icon() -> HICON {','fn tray_icon(resource_id: u16) -> HICON {'),
('PCWSTR(1 as *const u16), IMAGE_ICON','PCWSTR(resource_id as usize as *const u16), IMAGE_ICON'),
('    let tip = if enabled { "Snowhush — ON | Click: OFF" } else { "Snowhush — OFF | Click: ON" };',
 '''    let mode = APP.with_borrow(|app| app.as_ref().map(|a| a.settings.snow_mode)).unwrap_or(0);
    let tip = if !weather::in_season() { "Snowhush : du 1er octobre au 31 mars" }
        else if mode == 0 && enabled { "Snowhush : AUTO, NEIGE ACTIVE - clic pour couper" }
        else if mode == 0 { "Snowhush : AUTO, pas de neige - clic pour activer" }
        else if enabled { "Snowhush : neige ACTIVE - clic pour couper" }
        else { "Snowhush : neige COUPÉE - clic pour activer" };'''),
('        uFlags: NIF_TIP | NIF_SHOWTIP,\n        ..Default::default()\n    };\n    for (slot, unit) in data.szTip',
 '''        uFlags: NIF_TIP | NIF_SHOWTIP,
        ..Default::default()
    };
    if let Some(icon) = APP.with_borrow(|app| app.as_ref().map(|a| if enabled {a.icon} else {a.off_icon})) {
        data.hIcon = icon;
        data.uFlags |= NIF_ICON;
    }
    for (slot, unit) in data.szTip'''),
])

patch('windows/src/bin/setup.rs',[
('w!("Snowhush Setup")','w!("Installation de Snowhush PiiWii")'),
('format!("Version {VERSION}. Installs just for you, without administrator rights.")',
 'format!("Version française {VERSION}. Installation pour votre compte Windows, sans droits administrateur.")'),
('"INSTALL"','"INSTALLER"'),
('format!("Updates Snowhush {old} to {VERSION}. Your settings stay as they are.")',
 'format!("Mise à jour de Snowhush {old} vers {VERSION}. Vos réglages sont conservés.")'),
('"UPDATE"','"METTRE À JOUR"'),
('format!("Snowhush {old} is already here. Installing {VERSION} again repairs it.")',
 'format!("Snowhush {old} est déjà installé. Réinstaller {VERSION} répare le programme.")'),
('"REINSTALL"','"RÉINSTALLER"'),
('"INSTALLING…"','"INSTALLATION…"'),
('"LET IT SETTLE."','"C’EST INSTALLÉ !"'),
('"Snowhush is installed. You\'ll find it in the Start menu."',
 '"Snowhush PiiWii est installé. Il se trouve dans le menu Démarrer et près de l’horloge."'),
('"OPEN SNOWHUSH"','"OUVRIR SNOWHUSH"'),
('format!("Couldn\'t install: {error}.")','format!("Installation impossible : {error}.")'),
('"TRY AGAIN"','"RÉESSAYER"'),
])
patch('windows/src/welcome.rs',[
('const TITLE: &str = "IT\'S SNOWING\\nON YOUR DESKTOP.";',
 'const TITLE: &str = "IL NEIGE SUR\\nVOTRE BUREAU !";'),
('const BODY: &str = "Snowhush lives in the system tray. Click its icon to change the snowfall, let the drift melt, or keep the snow off your windows. Sweep it aside with your cursor.";',
 'const BODY: &str = "Snowhush se trouve près de l’horloge Windows. Cliquez sur l’interrupteur pour allumer ou couper la neige. Clic droit pour les réglages et le mode météo automatique. Disponible d’octobre à mars.";'),
('button: "GET STARTED"','button: "COMMENCER"'),
])
patch('windows/src/snow.rs',[
('format!("Snowhush couldn\'t start drawing snow.\\n\\n{error}")','format!("Snowhush ne peut pas afficher la neige.\\n\\n{error}")')
])
patch('windows/src/install.rs',[
('"the programs folder isn\'t available"','"le dossier des programmes est indisponible"'),
('"couldn\'t create {}: {error}"','"impossible de créer {} : {error}"'),
('"couldn\'t write {}: {error}"','"impossible d’écrire {} : {error}"'),
('"couldn\'t replace {}: {error}"','"impossible de remplacer {} : {error}"'),
('w!("Snow on your desktop")','w!("Neige animée sur le bureau")'),
])
patch('windows/src/update.rs',[
('"the latest release has no tag"','"la version officielle ne comporte pas de numéro"'),
('"Opening a connection"','"Ouverture de connexion"'),
('"Connecting to GitHub"','"Connexion à GitHub"'),
('"Preparing the request"','"Préparation de la requête"'),
('"GitHub answered {status}"','"GitHub a répondu {status}"'),
])
patch('windows/src/weather.rs',[
('w!("Snowhush-PiiWii/0.5.8")','w!("Snowhush-PiiWii-FR/0.5.9")')
])
patch('windows/build.rs',[
('const ICON: &str = "assets/Snowhush.ico";',
 'const ICON: &str = "assets/Snowhush.ico";\nconst SWITCH_ON: &str = "assets/switch-on.ico";\nconst SWITCH_OFF: &str = "assets/switch-off.ico";'),
('    println!("cargo:rerun-if-changed={ICON}");',
 '    println!("cargo:rerun-if-changed={ICON}");\n    println!("cargo:rerun-if-changed={SWITCH_ON}");\n    println!("cargo:rerun-if-changed={SWITCH_OFF}");'),
('    let icon_file = std::fs::read(ICON).expect("icon");',
 '    let icon_file = std::fs::read(ICON).expect("icon");\n    let switch_on = std::fs::read(SWITCH_ON).expect("switch ON");\n    let switch_off = std::fs::read(SWITCH_OFF).expect("switch OFF");'),
('        icon(&mut res, &icon_file);',
 '        icon(&mut res, &icon_file, 1, 1);\n        icon(&mut res, &switch_on, 2, 20);\n        icon(&mut res, &switch_off, 3, 30);'),
('fn icon(res: &mut Vec<u8>, file: &[u8]) {',
 'fn icon(res: &mut Vec<u8>, file: &[u8], group_id: u16, first_icon_id: u16) {'),
('        let id = i as u16 + 1;', '        let id = i as u16 + first_icon_id;'),
('    entry(res, RT_GROUP_ICON, 1, ENGLISH, &group);',
 '    entry(res, RT_GROUP_ICON, group_id, ENGLISH, &group);'),
('("CompanyName", "Mykhailo Bezruchko")',
 '("CompanyName", "Mykhailo Bezruchko — adaptation française PiiWii")'),
('("ProductName", "Snowhush")','("ProductName", "Snowhush PiiWii FR")')
])

# Pure stdlib PNG-in-ICO builder, no dependency on Pillow in the Windows runner.
def png(n, active):
    pixels=bytearray()
    for y in range(n):
        pixels.append(0)
        for x in range(n):
            X=(x+.5)*64/n;Y=(y+.5)*64/n
            rgba=(0,0,0,0)
            # Rounded background and switch track.
            dx=max(4-X,0,X-60);dy=max(15-Y,0,Y-49)
            if dx*dx+dy*dy<=16*16 and 4<=X<=60 and 15<=Y<=49:
                rgba=(12,24,40,255)
            if 7<=X<=57 and 18<=Y<=46:
                cornerX=min(X-7,57-X);cornerY=min(Y-18,46-Y)
                if cornerX>=13 or cornerY>=13 or (cornerX-13)**2+(cornerY-13)**2<=13**2:
                    rgba=(18,140,188,255) if active else (79,92,110,255)
            cx=43 if active else 21
            if (X-cx)**2+(Y-32)**2 <= 12**2: rgba=(249,253,255,255)
            pixels.extend(rgba)
    def chunk(tag, data):
        return struct.pack(">I",len(data))+tag+data+struct.pack(">I",zlib.crc32(tag+data)&0xFFFFFFFF)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',n,n,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(bytes(pixels),9))+chunk(b'IEND',b'')

def ico(active):
    sizes=[16,24,32,48,64,128,256]
    images=[png(n,active) for n in sizes]
    start=6+16*len(sizes)
    output=bytearray(struct.pack('<HHH',0,1,len(sizes)))
    for n,data in zip(sizes,images):
        output.extend(struct.pack('<BBBBHHII',n if n<256 else 0,n if n<256 else 0,0,0,1,32,len(data),start))
        start+=len(data)
    return bytes(output)+b''.join(images)

assets=root/'windows/assets'
(assets/'switch-on.ico').write_bytes(ico(True))
(assets/'switch-off.ico').write_bytes(ico(False))
print('SUCCESS Snowhush PiiWii FR 0.5.9')

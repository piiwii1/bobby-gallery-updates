#define MyAppName "PiiWii TV"
#define MyAppVersion "1.0.1"
#define MyAppPublisher "PiiWii"
#define MyAppExeName "PiiWiiTV.exe"

[Setup]
AppId={{A4BEA95B-8350-4DAE-AF09-1C3D7A4B9100}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\Programs\PiiWii TV
DefaultGroupName=PiiWii
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir=output
OutputBaseFilename=PiiWii-TV-Setup-1.0.1
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
SetupIconFile=PiiWiiTV.ico
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Files]
Source: "publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "MicrosoftEdgeWebview2Setup.exe"; DestDir: "{tmp}"; Flags: deleteafterinstall

[Icons]
Name: "{group}\PiiWii TV"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\PiiWii TV"; Filename: "{app}\{#MyAppExeName}"

[Registry]
Root: HKCU; Subkey: "Software\PiiWii\PiiWiiTV"; ValueType: string; ValueName: "InstallPath"; ValueData: "{app}\{#MyAppExeName}"; Flags: uninsdeletekey

[Run]
Filename: "{tmp}\MicrosoftEdgeWebview2Setup.exe"; Parameters: "/silent /install"; StatusMsg: "Vérification de Microsoft WebView2..."; Flags: waituntilterminated runhidden
Filename: "{app}\{#MyAppExeName}"; Description: "Lancer PiiWii TV"; Flags: nowait postinstall skipifsilent

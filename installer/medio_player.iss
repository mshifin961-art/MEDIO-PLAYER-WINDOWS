; MEDIO PLAYER — Windows Edition Inno Setup Installer Script
#define MyAppName "MEDIO PLAYER"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "MEDIO PLAYER"
#define MyAppURL "https://github.com/mshifin961-art/MEDIO-PLAYER-WINDOWS"
#define MyAppExeName "MEDIO PLAYER.exe"

[Setup]
AppId={{8D3F2C4A-91B7-4E6D-A512-7C8F3E4B9011}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={localappdata}\Programs\MEDIO PLAYER
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
PrivilegesRequired=lowest
OutputDir=Output
OutputBaseFilename=MEDIO-PLAYER-Windows-x64-Setup-1.0.0
SetupIconFile=..\src\main\resources\icons\medio_player.ico
Compression=lzma2/normal
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\build\compose\binaries\main\app\MEDIO PLAYER\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

; Note: User media library database (%LOCALAPPDATA%\MedioPlayer\medio_player.db) and cached thumbnails
; (%LOCALAPPDATA%\MedioPlayer\thumbnails) are intentionally stored outside {app} so upgrades and
; reinstalls preserve all user playlists, favorites, history, and settings.

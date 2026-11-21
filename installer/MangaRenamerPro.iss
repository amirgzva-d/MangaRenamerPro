#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif

#define AppName "Manga Renamer Pro"
#define AppPublisher "Manga Renamer Pro"
#define AppExeName "MangaRenamerPro.exe"

[Setup]
AppId={{A7F3C19B-88C4-4C13-8D76-6B63B38B3F21}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\Manga Renamer Pro
DefaultGroupName={#AppName}
UninstallDisplayIcon={app}\{#AppExeName}
OutputDir=artifacts
OutputBaseFilename=MangaRenamerPro-Setup-{#AppVersion}
ArchitecturesInstallIn64BitMode=x64
ArchitecturesAllowed=x64
PrivilegesRequired=admin
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
DisableProgramGroupPage=yes
SetupLogging=yes

[Tasks]
Name: "desktopicon"; Description: "ساخت میان‌بر روی دسکتاپ"; GroupDescription: "میان‌برها:"

[Files]
Source: "..\publish\{#AppExeName}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "اجرای {#AppName}"; Flags: nowait postinstall skipifsilent

; Installer for the standalone Open Roberta Lab on Windows. Built by build-windows.sh:
;   makensis -DSTAGE=<dir with OpenRobertaLab.exe, jre, lib, ...> -DOUTFILE=<setup exe> installer.nsi
; Installs for the current user only (no admin rights needed). User data (%LOCALAPPDATA%\OpenRobertaLab) is kept on uninstall.

Unicode true
SetCompressor /SOLID lzma

!include "MUI2.nsh"

!define APPNAME "Open Roberta Lab"
!define APPKEY "OpenRobertaLab"
!define UNINSTKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPKEY}"

Name "${APPNAME}"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\${APPKEY}"
InstallDirRegKey HKCU "Software\${APPKEY}" "InstallDir"
RequestExecutionLevel user

!define MUI_ICON "${STAGE}\NEPO.ico"
!define MUI_UNICON "${STAGE}\NEPO.ico"
!define MUI_FINISHPAGE_RUN "$INSTDIR\OpenRobertaLab.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Open Roberta Lab jetzt starten"
!define MUI_FINISHPAGE_SHOWREADME "$INSTDIR\LIESMICH.txt"
!define MUI_FINISHPAGE_SHOWREADME_TEXT "LIESMICH anzeigen"
!define MUI_FINISHPAGE_SHOWREADME_NOTCHECKED

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "German"

Section "Install"
    ; remove an old version first (outdated jars would break the server)
    RMDir /r "$INSTDIR\lib"
    RMDir /r "$INSTDIR\staticResources"
    RMDir /r "$INSTDIR\ora-cc-rsc"
    RMDir /r "$INSTDIR\jre"

    SetOutPath "$INSTDIR"
    File /r "${STAGE}\*.*"

    WriteUninstaller "$INSTDIR\Uninstall.exe"
    WriteRegStr HKCU "Software\${APPKEY}" "InstallDir" "$INSTDIR"
    WriteRegStr HKCU "${UNINSTKEY}" "DisplayName" "${APPNAME}"
    WriteRegStr HKCU "${UNINSTKEY}" "DisplayIcon" "$INSTDIR\OpenRobertaLab.exe"
    WriteRegStr HKCU "${UNINSTKEY}" "Publisher" "Open Roberta (local edition)"
    WriteRegStr HKCU "${UNINSTKEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
    WriteRegDWORD HKCU "${UNINSTKEY}" "NoModify" 1
    WriteRegDWORD HKCU "${UNINSTKEY}" "NoRepair" 1

    CreateDirectory "$SMPROGRAMS\${APPNAME}"
    CreateShortcut "$SMPROGRAMS\${APPNAME}\${APPNAME}.lnk" "$INSTDIR\OpenRobertaLab.exe" "" "$INSTDIR\OpenRobertaLab.exe"
    CreateShortcut "$SMPROGRAMS\${APPNAME}\${APPNAME} deinstallieren.lnk" "$INSTDIR\Uninstall.exe"
    CreateShortcut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\OpenRobertaLab.exe" "" "$INSTDIR\OpenRobertaLab.exe"
SectionEnd

Section "Uninstall"
    Delete "$DESKTOP\${APPNAME}.lnk"
    RMDir /r "$SMPROGRAMS\${APPNAME}"
    RMDir /r "$INSTDIR"
    DeleteRegKey HKCU "${UNINSTKEY}"
    DeleteRegKey HKCU "Software\${APPKEY}"
SectionEnd

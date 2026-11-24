; Installer of BlockCore (standalone block programming lab for Arduino, based on the Open Roberta Lab). Built by build-windows.sh:
;   makensis -DSTAGE=<dir with BlockCore.exe, jre, lib, ...> -DOUTFILE=<setup exe> installer.nsi
; Installs for the current user only (no admin rights needed). User data (%LOCALAPPDATA%\BlockCore) is kept on uninstall.

Unicode true
SetCompressor /SOLID lzma

!include "MUI2.nsh"

!define APPNAME "BlockCore"
!define APPKEY "BlockCore"
!define UNINSTKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPKEY}"

Name "${APPNAME}"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\${APPKEY}"
InstallDirRegKey HKCU "Software\${APPKEY}" "InstallDir"
RequestExecutionLevel user

!define MUI_ICON "${STAGE}\BlockCore.ico"
!define MUI_UNICON "${STAGE}\BlockCore.ico"
!define MUI_FINISHPAGE_RUN "$INSTDIR\BlockCore.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Start BlockCore now"
!define MUI_FINISHPAGE_SHOWREADME "$INSTDIR\README.txt"
!define MUI_FINISHPAGE_SHOWREADME_TEXT "Show README"
!define MUI_FINISHPAGE_SHOWREADME_NOTCHECKED

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

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
    WriteRegStr HKCU "${UNINSTKEY}" "DisplayIcon" "$INSTDIR\BlockCore.exe"
    WriteRegStr HKCU "${UNINSTKEY}" "Publisher" "Marvin Ramm"
    WriteRegStr HKCU "${UNINSTKEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
    WriteRegDWORD HKCU "${UNINSTKEY}" "NoModify" 1
    WriteRegDWORD HKCU "${UNINSTKEY}" "NoRepair" 1

    CreateDirectory "$SMPROGRAMS\${APPNAME}"
    CreateShortcut "$SMPROGRAMS\${APPNAME}\${APPNAME}.lnk" "$INSTDIR\BlockCore.exe" "" "$INSTDIR\BlockCore.exe"
    CreateShortcut "$SMPROGRAMS\${APPNAME}\Uninstall ${APPNAME}.lnk" "$INSTDIR\Uninstall.exe"
    CreateShortcut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\BlockCore.exe" "" "$INSTDIR\BlockCore.exe"
SectionEnd

Section "Uninstall"
    Delete "$DESKTOP\${APPNAME}.lnk"
    RMDir /r "$SMPROGRAMS\${APPNAME}"
    RMDir /r "$INSTDIR"
    DeleteRegKey HKCU "${UNINSTKEY}"
    DeleteRegKey HKCU "Software\${APPKEY}"
SectionEnd

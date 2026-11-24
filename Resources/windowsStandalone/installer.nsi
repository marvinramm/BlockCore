; Installer of BlockCore (standalone block programming lab for Arduino, based on the Open Roberta Lab). Built by build-windows.sh:
;   makensis -DVERSION=<version> -DSTAGE=<dir with BlockCore.exe, jre, lib, ...> -DOUTFILE=<setup exe> installer.nsi
; Installs for the current user only (no admin rights needed). User data (%LOCALAPPDATA%\BlockCore) is kept on uninstall.
; With /UPDATE (started by BlockCore.exe for an update) no questions are asked: only the progress is shown, then BlockCore starts again.

Unicode true
SetCompressor /SOLID lzma

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"

!define APPNAME "BlockCore"
!define APPKEY "BlockCore"
!ifndef VERSION
    !define VERSION "dev"
!endif
!define UNINSTKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APPKEY}"

Name "${APPNAME} ${VERSION}"
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

Var Update

Function .onInit
    StrCpy $Update 0
    ${GetParameters} $0
    ClearErrors
    ${GetOptions} $0 "/UPDATE" $1
    ${IfNot} ${Errors}
        StrCpy $Update 1
        SetAutoClose true
    ${EndIf}
FunctionEnd

; an update shows the progress only
Function SkipOnUpdate
    ${If} $Update == 1
        Abort
    ${EndIf}
FunctionEnd

!define MUI_PAGE_CUSTOMFUNCTION_PRE SkipOnUpdate
!insertmacro MUI_PAGE_WELCOME
!define MUI_PAGE_CUSTOMFUNCTION_PRE SkipOnUpdate
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_PAGE_CUSTOMFUNCTION_PRE SkipOnUpdate
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Section "Install"
    ${If} $Update == 1
        DetailPrint "Updating BlockCore to ${VERSION} ..."
        ; BlockCore.exe and its server have just been stopped: give them a moment to end
        Sleep 2000
    ${EndIf}
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
    WriteRegStr HKCU "${UNINSTKEY}" "DisplayVersion" "${VERSION}"
    WriteRegStr HKCU "${UNINSTKEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
    WriteRegDWORD HKCU "${UNINSTKEY}" "NoModify" 1
    WriteRegDWORD HKCU "${UNINSTKEY}" "NoRepair" 1

    CreateDirectory "$SMPROGRAMS\${APPNAME}"
    CreateShortcut "$SMPROGRAMS\${APPNAME}\${APPNAME}.lnk" "$INSTDIR\BlockCore.exe" "" "$INSTDIR\BlockCore.exe"
    CreateShortcut "$SMPROGRAMS\${APPNAME}\Uninstall ${APPNAME}.lnk" "$INSTDIR\Uninstall.exe"
    CreateShortcut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\BlockCore.exe" "" "$INSTDIR\BlockCore.exe"

    ${If} $Update == 1
        Exec '"$INSTDIR\BlockCore.exe"'
    ${EndIf}
SectionEnd

Section "Uninstall"
    Delete "$DESKTOP\${APPNAME}.lnk"
    RMDir /r "$SMPROGRAMS\${APPNAME}"
    RMDir /r "$INSTDIR"
    DeleteRegKey HKCU "${UNINSTKEY}"
    DeleteRegKey HKCU "Software\${APPKEY}"
SectionEnd

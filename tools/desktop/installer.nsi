; Установщик Hush Till Six для Windows (NSIS 3). Собирает build.js:  makensis -DSRC=папка_игры -DOUT=файл.exe -DVER=1.0.0 -DICON=…ico -DSIDE=…bmp -DHEAD=…bmp installer.nsi
; Ставит игру в профиль игрока, без прав администратора: %LOCALAPPDATA%\Programs\Hush Till Six; ярлыки в «Пуске» и на рабочем столе;
; удаление — «Параметры → Приложения» (или «Uninstall Hush Till Six.exe» в папке игры). Сохранения (%APPDATA%\Hush Till Six) при удалении остаются.
; Язык установщика — по языку Windows: русский, украинский, остальные — английский.
Unicode true
ManifestDPIAware true
SetCompressor /SOLID lzma
SetCompressorDictSize 32
RequestExecutionLevel user

!define NAME "Hush Till Six"
!define EXE "Hush Till Six.exe"
!define UNEXE "Uninstall Hush Till Six.exe"
!define PUB "Office Dreams"
!define UNKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\HushTillSix"

Name "${NAME}"
OutFile "${OUT}"
InstallDir "$LOCALAPPDATA\Programs\${NAME}"
InstallDirRegKey HKCU "${UNKEY}" "InstallLocation"
BrandingText "${NAME} ${VER}"

!include "MUI2.nsh"
!include "FileFunc.nsh"
!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${SIDE}"
!define MUI_UNWELCOMEFINISHPAGE_BITMAP "${SIDE}"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_RIGHT
!define MUI_HEADERIMAGE_BITMAP "${HEAD}"
!define MUI_HEADERIMAGE_UNBITMAP "${HEAD}"
!define MUI_ABORTWARNING

!insertmacro MUI_PAGE_WELCOME
!define MUI_PAGE_CUSTOMFUNCTION_LEAVE DirLeave
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\${EXE}"
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Russian"
!insertmacro MUI_LANGUAGE "Ukrainian"
LangString Running ${LANG_ENGLISH} "Hush Till Six is running. Close the game and try again."
LangString Running ${LANG_RUSSIAN} "Hush Till Six сейчас запущена. Закройте игру и попробуйте ещё раз."
LangString Running ${LANG_UKRAINIAN} "Hush Till Six зараз запущена. Закрийте гру і спробуйте ще раз."
LangString NotOurs ${LANG_ENGLISH} "This folder does not look like a Hush Till Six installation. Nothing was removed."
LangString NotOurs ${LANG_RUSSIAN} "Эта папка не похожа на установленную Hush Till Six. Ничего не удалено."
LangString NotOurs ${LANG_UKRAINIAN} "Ця тека не схожа на встановлену Hush Till Six. Нічого не видалено."

VIProductVersion "${VER}.0"
VIAddVersionKey /LANG=${LANG_ENGLISH} "ProductName" "${NAME}"
VIAddVersionKey /LANG=${LANG_ENGLISH} "CompanyName" "${PUB}"
VIAddVersionKey /LANG=${LANG_ENGLISH} "LegalCopyright" "© 2026 ${PUB}"
VIAddVersionKey /LANG=${LANG_ENGLISH} "FileDescription" "${NAME} Setup"
VIAddVersionKey /LANG=${LANG_ENGLISH} "FileVersion" "${VER}"
VIAddVersionKey /LANG=${LANG_ENGLISH} "ProductVersion" "${VER}"

Function .onInit   ; игра запущена — сначала закрыть
  FindWindow $0 "Chrome_WidgetWin_1" "${NAME}"
  IntCmp $0 0 free
    MessageBox MB_OK|MB_ICONEXCLAMATION "$(Running)" /SD IDOK
    Abort
  free:
FunctionEnd
Function un.onInit
  FindWindow $0 "Chrome_WidgetWin_1" "${NAME}"
  IntCmp $0 0 free
    MessageBox MB_OK|MB_ICONEXCLAMATION "$(Running)" /SD IDOK
    Abort
  free:
  ${GetFileName} "$INSTDIR" $1   ; удаляем только свою папку «Hush Till Six»
  StrCmp $1 "${NAME}" ours
    MessageBox MB_OK|MB_ICONEXCLAMATION "$(NotOurs)" /SD IDOK
    Abort
  ours:
FunctionEnd
Function DirLeave   ; выбрали другую папку — игра ляжет в подпапку «Hush Till Six», а не прямо в неё
  ${GetFileName} "$INSTDIR" $1
  StrCmp $1 "${NAME}" done
    StrCpy $INSTDIR "$INSTDIR\${NAME}"
  done:
FunctionEnd

Section "Hush Till Six"
  SetShellVarContext current
  SetOutPath "$INSTDIR"
  RMDir /r "$INSTDIR\resources"   ; обновление поверх старой версии: старые файлы игры не остаются
  RMDir /r "$INSTDIR\locales"
  File /r "${SRC}/*"
  WriteUninstaller "$INSTDIR\${UNEXE}"
  CreateShortcut "$SMPROGRAMS\${NAME}.lnk" "$INSTDIR\${EXE}" "" "$INSTDIR\${EXE}" 0
  CreateShortcut "$DESKTOP\${NAME}.lnk" "$INSTDIR\${EXE}" "" "$INSTDIR\${EXE}" 0
  WriteRegStr HKCU "${UNKEY}" "DisplayName" "${NAME}"
  WriteRegStr HKCU "${UNKEY}" "DisplayVersion" "${VER}"
  WriteRegStr HKCU "${UNKEY}" "Publisher" "${PUB}"
  WriteRegStr HKCU "${UNKEY}" "DisplayIcon" '"$INSTDIR\${EXE}",0'
  WriteRegStr HKCU "${UNKEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNKEY}" "UninstallString" '"$INSTDIR\${UNEXE}"'
  WriteRegStr HKCU "${UNKEY}" "QuietUninstallString" '"$INSTDIR\${UNEXE}" /S'
  WriteRegDWORD HKCU "${UNKEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNKEY}" "NoRepair" 1
  ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
  IntFmt $0 "0x%08X" $0
  WriteRegDWORD HKCU "${UNKEY}" "EstimatedSize" "$0"
SectionEnd

Section "Uninstall"
  SetShellVarContext current
  Delete "$SMPROGRAMS\${NAME}.lnk"
  Delete "$DESKTOP\${NAME}.lnk"
  RMDir /r "$INSTDIR"
  DeleteRegKey HKCU "${UNKEY}"
SectionEnd

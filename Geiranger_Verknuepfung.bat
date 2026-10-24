@echo off
rem Legt die Verknuepfung "Geiranger" mit dem Symbol Geiranger.ico auf den Desktop und ins Startmenue (Phase 18, F23).
rem Ziel ist javaw mit dist\Geiranger.jar wie in Geiranger.bat; vorher in NetBeans Clean and Build ausfuehren.
cd /d "%~dp0"
if not exist dist\Geiranger.jar (echo dist\Geiranger.jar fehlt: bitte in NetBeans Clean and Build ausfuehren. & pause & exit /b 1)
if not exist Geiranger.ico (echo Geiranger.ico fehlt: ant symbol & pause & exit /b 1)
set "JAVAW=javaw.exe"
for /f "delims=" %%j in ('where javaw 2^>nul') do if not defined JAVAW_FOUND (set "JAVAW=%%j" & set "JAVAW_FOUND=1")
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ws = New-Object -ComObject WScript.Shell;" ^
  "$javaArgs = '-Xmx6g -Dsun.java2d.uiScale.enabled=true --enable-native-access=ALL-UNNAMED --add-opens java.desktop/sun.awt=ALL-UNNAMED --add-opens java.desktop/sun.awt.windows=ALL-UNNAMED --add-opens java.desktop/sun.java2d=ALL-UNNAMED -jar dist\Geiranger.jar';" ^
  "foreach ($ziel in @([Environment]::GetFolderPath('Desktop'), (Join-Path ([Environment]::GetFolderPath('Programs')) ''))) {" ^
  "  $l = $ws.CreateShortcut((Join-Path $ziel 'Geiranger.lnk'));" ^
  "  $l.TargetPath = '%JAVAW%'; $l.Arguments = $javaArgs; $l.WorkingDirectory = '%CD%';" ^
  "  $l.IconLocation = '%CD%\Geiranger.ico,0'; $l.Description = 'Geiranger - Sieben Schwestern und elf Kehren';" ^
  "  $l.Save(); Write-Host ('Verknuepfung: ' + $l.FullName) }"
if errorlevel 1 (echo Die Verknuepfung liess sich nicht anlegen. & pause & exit /b 1)
echo Fertig: "Geiranger" auf dem Desktop und im Startmenue.
pause

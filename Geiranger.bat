@echo off
rem Geiranger ohne NetBeans starten (nach Clean and Build)
cd /d "%~dp0"
if not exist dist\Geiranger.jar (echo dist\Geiranger.jar fehlt: bitte in NetBeans Clean and Build ausfuehren. & pause & exit /b 1)
start "" javaw -Xmx6g -Dsun.java2d.uiScale.enabled=true --enable-native-access=ALL-UNNAMED --add-opens java.desktop/sun.awt=ALL-UNNAMED --add-opens java.desktop/sun.awt.windows=ALL-UNNAMED --add-opens java.desktop/sun.java2d=ALL-UNNAMED -jar dist\Geiranger.jar

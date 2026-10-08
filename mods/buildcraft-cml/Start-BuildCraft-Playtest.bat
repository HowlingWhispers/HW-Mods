@echo off
setlocal EnableExtensions
title H.O.W.L. BuildCraft Singleplayer Playtest
cd /d "%~dp0"
if not exist "CodaLoader.jar" (
  echo ERROR: Extract the complete playtest ZIP before launching.
  pause
  exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo ERROR: Java 25 must be on your PATH for Minecraft 26.4 Snapshot 3.
  pause
  exit /b 1
)
REM Local single-player profile without online entitlement. This deliberately
REM skips release auto-update for a throwaway developer test only.
set "CODA_LAUNCHED_BY=CodaLauncher"
set "CODA_NO_PAUSE=1"
set "CODA_PLAYER_NAME="
set "CODA_PLAYER_UUID="
set "CODA_PLAY_MODE="
set "CODA_ACCESS_TOKEN="
set "CODA_AUTH_CLIENT_ID="
echo H.O.W.L. BuildCraft glass pipe experiment. Never use cherished saves.
java -jar "CodaLoader.jar" --root run
set "EXIT_CODE=%ERRORLEVEL%"
if not "%EXIT_CODE%"=="0" echo Playtest exited with code %EXIT_CODE%.
pause
exit /b %EXIT_CODE%

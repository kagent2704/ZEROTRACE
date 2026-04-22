@echo off
setlocal

if "%ZEROTRACE_SERVER_URL%"=="" (
  echo ZEROTRACE_SERVER_URL is not set.
  echo Launching client anyway. Enter the server URL in the app login screen.
) else (
  echo Using ZEROTRACE_SERVER_URL=%ZEROTRACE_SERVER_URL%
)

java -jar "%~dp0target\zerotrace-core-1.0-SNAPSHOT.jar"

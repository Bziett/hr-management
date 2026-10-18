@echo off
cd /d "%~dp0"
java -Xms16m -Xmx64m -XX:+UseSerialGC StartWeb
pause

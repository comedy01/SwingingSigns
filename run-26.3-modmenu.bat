@echo off
cd /d C:\Users\Alten\IdeaProjects\SwingingSigns
set JAVA_HOME=%USERPROFILE%\.jdks\ms-25.0.4.1
call gradlew.bat runClient -Pminecraft_version=26.3 -Ploader=fabric -Pmodmenu --console=plain
pause

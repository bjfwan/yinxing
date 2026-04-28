@echo off
setlocal
set "JAVA_HOME=D:\Android\jbr"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JAVA_HOME is invalid: %JAVA_HOME%
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "%~dp0gradlew.bat" %*
exit /b %ERRORLEVEL%

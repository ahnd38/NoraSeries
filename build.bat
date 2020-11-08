@echo off

cd %~dp0

echo 1. Check Java runtime...
call java -version
if errorlevel 1 (
  echo ^[ERROR^] Java runtime is not detected^, please install Java Development Kit^(8 or higher^) first ^!
  pause >nul
  exit
)

echo.

echo 2. Check Maven...
call mvn -v
if errorlevel 1 (
  echo ^[ERROR^] ApacheMaven is not installed^, please install ApacheMaven first ^!
  pause >nul
  exit
)

echo.

echo 3. Check Git...
call git --version
if errorlevel 1 (
  echo ^[ERROR^] Git is not installed^, please install Git first ^!
  pause >nul
  exit
)
for /f "usebackq" %%A in (`git config user.name""`) do set GIT_USERNAME=%%A
echo USERNAME  = %GIT_USERNAME%
for /f "usebackq" %%A in (`git config user.email""`) do set GIT_USEREMAIL=%%A
echo USEREMAIL = %GIT_USEREMAIL%

if "%GIT_USERNAME%" == "" (
  echo Git user name is not set^, please set ^'git config user^.name USERNAME^' ^!
  pause >nul
  exit
)
if "%GIT_USEREMAIL%" == "" (
  echo Git user email is not set^, please set ^'git config user^.email USEREMAIL^' ^!
  pause >nul
  exit
)

echo.

echo 4. Building...
if exist "%~dp0keystore" (
  call mvn clean compile install --settings ./.mvn/local-settings.xml
) else (
  call mvn clean compile install jarsigner:sign -Djarsigner.skip=true
)

if errorlevel 1 (
  echo "[ERROR] Build error !"
  pause >nul
  exit
)

echo.

echo 5. Copy Application Files...
if not exist "%~dp0dist\" (
  mkdir "%~dp0dist\"
)
if exist "%~dp0src\NoraGateway\target" (
  copy "%~dp0src\NoraGateway\target\*.zip" "%~dp0dist\"
)
if exist "%~dp0src\NoraDStarProxyGateway\target" (
  copy "%~dp0src\NoraDStarProxyGateway\target\*.zip" "%~dp0dist\"
)
if exist "%~dp0src\NoraExternalConnector\target" (
  copy "%~dp0src\NoraExternalConnector\target\*.zip" "%~dp0dist\"
)
if exist "%~dp0src\ircDDBServer\target" (
  copy "%~dp0src\ircDDBServer\target\*.jar" "%~dp0dist\"
)
if exist "%~dp0src\NoraHelper\target" (
  copy "%~dp0src\NoraHelper\target\*.jar" "%~dp0dist\"
)
if exist "%~dp0src\NoraUpdater\target" (
  copy "%~dp0src\NoraUpdater\target\*.jar" "%~dp0dist\"
)
if exist "%~dp0src\KdkAPI\target" (
  copy "%~dp0src\KdkAPI\target\*.zip" "%~dp0dist\"
)

echo.

echo 5. Clean cache files...
call mvn clean

echo.
echo  __________________________________________________
echo ^|                                                  ^|
echo ^| Build complete !                                 ^|
echo ^|__________________________________________________^|

pause >nul

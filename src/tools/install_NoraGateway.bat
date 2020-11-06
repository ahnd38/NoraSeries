@echo off
CLS
echo  __________________________
echo ^|                          ^|
echo ^| NoraGateway Auto Updater ^|
echo ^|__________________________^|
echo.

:init
 setlocal DisableDelayedExpansion
 set cmdInvoke=1
 set winSysFolder=System32
 set "batchPath=%~0"
 for %%k in (%0) do set batchName=%%~nk
 set "vbsGetPrivileges=%temp%\OEgetPriv_%batchName%.vbs"
 setlocal EnableDelayedExpansion

:checkPrivileges
  NET FILE 1>NUL 2>NUL
  if '%errorlevel%' == '0' ( goto gotPrivileges ) else ( goto getPrivileges )

:getPrivileges
  if '%1'=='ELEV' (echo ELEV & shift /1 & goto gotPrivileges)

  echo Set UAC = CreateObject^("Shell.Application"^) > "%vbsGetPrivileges%"
  echo args = "ELEV " >> "%vbsGetPrivileges%"
  echo For Each strArg in WScript.Arguments >> "%vbsGetPrivileges%"
  echo args = args ^& strArg ^& " "  >> "%vbsGetPrivileges%"
  echo Next >> "%vbsGetPrivileges%"

  if '%cmdInvoke%'=='1' goto InvokeCmd 

  echo UAC.ShellExecute "!batchPath!", args, "", "runas", 1 >> "%vbsGetPrivileges%"
  goto ExecElevation

:InvokeCmd
  echo args = "/c """ + "!batchPath!" + """ " + args >> "%vbsGetPrivileges%"
  echo UAC.ShellExecute "%SystemRoot%\%winSysFolder%\cmd.exe", args, "", "runas", 1 >> "%vbsGetPrivileges%"

:ExecElevation
 "%SystemRoot%\%winSysFolder%\WScript.exe" "%vbsGetPrivileges%" %*
 exit /B

:gotPrivileges
 setlocal & cd /d %~dp0
 if '%1'=='ELEV' (del "%vbsGetPrivileges%" 1>nul 2>nul  &  shift /1)


echo 1. Check Java runtime...
java -version
if errorlevel 1 (
  echo [ERROR] Java runtime is not detected, please install java runtime first !
  pause >nul
  exit
)

echo.
echo 2. Downloading nora series updater...
powershell "(new-object System.Net.WebClient).Downloadfile('https://k-dk.net/nora-release/NoraUpdater.jar', 'NoraUpdater.jar')"

echo.
if exist NoraUpdater.jar (
  echo 3. Executing updater...
  java -jar .\NoraUpdater.jar -t NoraGateway -f -d %~dp0 %*
  
  del NoraUpdater.jar
) else (
  echo [ERROR] Failed to download Nora series updater !
  pause >nul
  exit
)

if not exist "%~dp0config\NoraGateway.xml" (
  echo.
  echo Configuration file is not found, creating copy of %~dp0config\NoraGateway.xml
  copy "%~dp0config\NoraGateway.xml.default" "%~dp0config\NoraGateway.xml" >nul
)

echo.
echo  __________________________________________________
echo ^|                                                  ^|
echo ^| Update complete !                                ^|
echo ^|                                                  ^|
echo ^| [Edit Configuration] ^*^*^* YOU MUST EDIT ^*^*^* ^|
echo ^|   %~dp0config^\NoraGateway.xml
echo ^| [Test start]                                     ^|
echo ^|   %~dp0start.bat
echo ^|__________________________________________________^|


pause >nul

@echo off

cd /d %~dp0

if not exist .\config\WebRemoteControlUsers.xml (
    copy .\config\WebRemoteControlUsers.xml.default .\config\WebRemoteControlUsers.xml
)

for %%f in (.\NoraGateway*.jar) do set CLASSPATH=%%f;%CLASSPATH%

start cmd /c .\startHelper.bat

java -classpath "%CLASSPATH%" org.jp.illg.nora.NoraGateway

@pause

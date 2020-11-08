#!/bin/bash

WORK_DIR=$(cd $(dirname $0);pwd)
cd $WORK_DIR

Usage="Usage : update.sh -u ExecuteUser NoraGateway_vx.x.x.zip"

ExecuteUser="$SUDO_USER"

if [ ! "$USER" = "root" ]; then
    echo "Please execute sudo or root !" 1>&2
    exit 1
fi

while getopts u: OPT
do
  case $OPT in
    "u" ) ExecuteUser="$OPTARG" ;;
    * ) echo "$Usage" 1>&2
        exit 1 ;;
  esac
done

shift `expr $OPTIND - 1`

ArchiveFilePath="$1"

if [ ! -e "$ArchiveFilePath" ]; then
    echo "Could not found $ArchiveFilePath" 1>&2
    exit 1
fi


echo "NoraGateway SimpleUpdater"
echo ""
echo "User:$ExecuteUser"
echo ""

echo "[1] Backup config file..."
cp ./config/NoraGateway.xml ./config/NoraGateway.xml.backup

echo "[2] Backup hosts file..."
cp ./config/hosts.txt ./config/hosts.txt.backup

echo "[3] Remove NoraGateway application jar"
rm ./NoraGateway*.jar

echo "[4] Extract $1"
unzip -o $1 -d ../ >/dev/null 2>&1

echo "[5] Change Permission"
if [ ! -e ./NoraGateway.status ]; then
    touch ./NoraGateway.status
fi
chown -R $ExecuteUser:$ExecuteUser ../NoraGateway
chmod 755 ./log
chmod 644 ./log/*
chmod 644 ./NoraGateway.status
chmod 744 ./*.sh

echo "[6] Remove hosts.output.txt"
if [ -e ./hosts.output.txt ]; then
    rm ./hosts.output.txt
fi

echo "[7] Generate configuration diff to ./config/NoraGateway.xml.diff"
if [ -e ./config/NoraGateway.xml ]; then
    diff ./config/NoraGateway.xml.default ./config/NoraGateway.xml > ./config/NoraGateway.xml.diff
fi

echo "Complete !"

exit 0

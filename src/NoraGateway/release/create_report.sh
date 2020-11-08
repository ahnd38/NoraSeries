#!/bin/bash

DUMP_COUNT=5
DUMP_INTERVAL_SECONDS=5

WORK_DIR=$(cd $(dirname $0);pwd)
cd $WORK_DIR

ExecuteUser="$SUDO_USER"
Usage="create_report.sh -u ExecuteUser"

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

PID=`sudo -u $ExecuteUser jps -v | grep NoraGateway | awk '{print $1}'`

echo "NoraGateway Simple Report Creater"
echo ""
echo "PID=$PID"

DUMP_HOME="NoraGateway."`date +%Y%m%d%H%M%S`".dump"

sudo -u $ExecuteUser mkdir -p "$DUMP_HOME"

echo "[1] Dump threads..."
if [ "$PID" = "" ]; then
    echo "    NoraGateway instance not found !" 1>&2
else
    for ((i=1; i <= $DUMP_COUNT; i++))
    do
       sudo -u $ExecuteUser jstack -l $PID >> "./$DUMP_HOME/$DUMP_HOME.$i.txt"
       echo "    #" $i
       if [ $i -lt $DUMP_COUNT ]; then
           echo "      Sleeping..."
           sleep "$DUMP_INTERVAL_SECONDS"
       fi
    done
fi
echo "[2] Copy logs..."
sudo -u $ExecuteUser cp ./log/*.log "./$DUMP_HOME"

echo "[3] Copy configration file..."
sudo -u $ExecuteUser cp "./config/NoraGateway.xml" "./$DUMP_HOME"

echo "[4] Create archive..."
sudo -u $ExecuteUser tar -Jcf "$DUMP_HOME.tar.xz" "$DUMP_HOME"

echo "[5] Cleanup..."
sudo -u $ExecuteUser rm -R -d -f "./$DUMP_HOME"

echo ""
echo "#################################################"
echo "# Complete !"
echo "#   PLEASE EMAIL TO"
echo "#     kenoh_doyu@txb.sakura.ne.jp"
echo "#     [$DUMP_HOME.tar.xz]"
echo "#################################################"


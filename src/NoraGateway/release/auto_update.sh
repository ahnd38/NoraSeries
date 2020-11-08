#!/bin/bash

WORK_DIR=$(cd $(dirname $0);pwd)
cd $WORK_DIR

if [ "$EUID" -ne 0 ]
  then echo "Please run as root! (sudo ${0##*/})"
  exit
fi

while getopts :u: OPT
do
  case $OPT in
    "u" ) SETUP_USER="$OPTARG" ;;
  esac
done

echo "1. Downloading updater..."
wget -q "https://k-dk.net/nora-release/NoraUpdater.jar" -O "NoraUpdater.jar"
if [ $? -ne 0 ]
  then echo "Could not download nora serias updater !"
  exit
fi

echo "2. Executing updater..."
java -jar ./NoraUpdater.jar -u ${SETUP_USER:-$SUDO_USER} -d "$WORK_DIR" $*
if [ $? -ne 0 ]
  then echo "Update error !"
  exit
fi

printf " __________________________________________________\n"
printf "|                                                  |\n"
printf "| Update complete !                                |\n"
printf "|                                                  |\n"
printf "| [Edit Configuration] *** YOU MUST EDIT ***       |\n"
printf "|   nano %s/config/NoraGateway.xml                 \n" ${WORK_DIR:?}
printf "| [Test start]                                     |\n"
printf "|   %s/start.sh                                    \n" ${WORK_DIR:?}
printf "| [Start background service]                       |\n"
printf "|   sudo systemctl start NoraGateway.service       |\n"
printf "|__________________________________________________|\n"

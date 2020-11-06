#!/bin/bash

INSTALL_DIR=/opt/NoraGateway
SETUP_USER=${SUDO_USER}

WORK_DIR=$(cd $(dirname $0);pwd)
cd $WORK_DIR

if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

apt update -y
apt install -y software-properties-common

wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | apt-key add -
add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/

apt update -y

apt install -y adoptopenjdk-11-hotspot

apt clean

wget -q "https://k-dk.net/nora-release/NoraUpdater.jar" -O "NoraUpdater.jar"
if [[ ! -e ${INSTALL_DIR:?}/NoraUpdater.jar ]]; then
  mkdir ${INSTALL_DIR:?}
  cp ./NoraUpdater.jar ${INSTALL_DIR:?}/
  chown ${SETUP_USER:?}:${SETUP_USER:?} ${INSTALL_DIR:?}/NoraUpdater.jar 
fi

java -jar ./NoraUpdater.jar -y -f -u ${SETUP_USER:?} -t NoraGateway -d "${INSTALL_DIR:?}"

if [[ ! -e ${INSTALL_DIR:?}/config/NoraGateway.xml ]]; then
  cp ${INSTALL_DIR:?}/config/NoraGateway.xml.default ${INSTALL_DIR:?}/config/NoraGateway.xml
  chown ${SETUP_USER:?}:${SETUP_USER:?} ${INSTALL_DIR:?}/config/NoraGateway.xml
fi

nano ${INSTALL_DIR:?}/config/NoraGateway.xml

systemctl enable NoraGateway

rm ./NoraUpdater.jar

printf " __________________________________________________\n"
printf "|                                                  \n"
printf "| Install complete !                               \n"
printf "|                                                  \n"
printf "| [Edit Configuration] *** YOU MUST EDIT ***       \n"
printf "|   nano %s/config/NoraGateway.xml                 \n" ${INSTALL_DIR:?}
printf "| [Test start]                                     \n"
printf "|   %s/start.sh                                    \n" ${INSTALL_DIR:?}
printf "| [Start background service]                       \n"
printf "|   sudo systemctl start NoraGateway.service       \n"
printf "|__________________________________________________\n"

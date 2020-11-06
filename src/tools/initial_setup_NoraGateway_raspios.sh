#!/bin/bash
APP_TYPE=NoraGateway
INSTALL_DIR=/opt/${APP_TYPE:?}
SETUP_USER=${SUDO_USER}
ARCH=`uname -m`

WORK_DIR=$(cd $(dirname $0);pwd)
cd $WORK_DIR

if [ "$EUID" -ne 0 ]
  then echo "Please run as root! (sudo ${0##*/})"
  exit
fi

printf "=== Start ${APP_TYPE:?} Initial Setup Script ===\n"
printf "\n"
printf "[Configuration]\n"
printf "  Application Type   %s\n" ${APP_TYPE:?}
printf "  Install Directory  %s\n" ${INSTALL_DIR:?}
printf "  Setup User Name    %s\n" ${SETUP_USER:?}
printf "  CPU Architecture   %s\n" ${ARCH:?}
printf "\n"

apt update -y
apt install -y apt-transport-https ca-certificates wget dirmngr gnupg software-properties-common lsb-release

if [ $(echo $ARCH | grep -e 'armv6') ]; then
  apt install -y openjdk-8-jdk 
else
  RASPIOS_CODENAME=`lsb_release -a | sed -r -n 's/Codename:\s*(\S*)/\1/p'`
  if [ -z "${RASPIOS_CODENAME}" ] ; then
    echo "Could not get RaspberryPi OS code name !"
    exit
  fi

  wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | apt-key add -
  grep -q "deb https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/ ${RASPIOS_CODENAME:?} main Release" /etc/apt/sources.list || echo "deb https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/ ${RASPIOS_CODENAME:?} main Release" >> /etc/apt/sources.list

  apt update -y

  apt install -y adoptopenjdk-11-hotspot
fi

apt clean

wget -q "https://k-dk.net/nora-release/NoraUpdater.jar" -O "NoraUpdater.jar"
if [[ ! -e ${INSTALL_DIR:?}/NoraUpdater.jar ]]; then
  mkdir ${INSTALL_DIR:?}
  cp ./NoraUpdater.jar ${INSTALL_DIR:?}/
  chown ${SETUP_USER:?}:${SETUP_USER:?} ${INSTALL_DIR:?}/NoraUpdater.jar 
fi

java -jar ./NoraUpdater.jar -f -y -u ${SETUP_USER:?} -t ${APP_TYPE:?} -d "${INSTALL_DIR:?}"

if [[ ! -e ${INSTALL_DIR:?}/config/${APP_TYPE:?}.xml ]]; then
  cp ${INSTALL_DIR:?}/config/${APP_TYPE:?}.xml.default ${INSTALL_DIR:?}/config/${APP_TYPE:?}.xml
  chown ${SETUP_USER:?}:${SETUP_USER:?} ${INSTALL_DIR:?}/config/${APP_TYPE:?}.xml
fi

usermod -aG dialout ${SETUP_USER:?}

nano ${INSTALL_DIR:?}/config/${APP_TYPE:?}.xml

systemctl enable ${APP_TYPE:?} 

rm ./NoraUpdater.jar

printf " __________________________________________________\n"
printf "|                                                  \n"
printf "| Install complete !                               \n"
printf "|                                                  \n"
printf "| [Edit Configuration] *** YOU MUST EDIT ***       \n"
printf "|\n"
printf "|   nano %s/config/%s.xml\n" ${INSTALL_DIR:?} ${APP_TYPE:?}
printf "|\n"
printf "| [Test start]                                     \n"
printf "|   %s/start.sh                                    \n" ${INSTALL_DIR:?}
printf "|\n"
printf "| [Start background service]                       \n"
printf "|   sudo systemctl start %s.service\n" ${APP_TYPE:?}
printf "|__________________________________________________\n"

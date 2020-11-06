#!/bin/bash

WORK_DIR=$(cd $(dirname $0);pwd)
cd $WORK_DIR

printf "1. Checking JavaCompiler...\n"
if !(type "javac" > /dev/null 2>&1); then
    printf "Please install JavaDevelopmentKit(8 or higher) first !\n"
    exit
fi
javac -version

printf "\n"

printf "2. Checking Maven...\n"
if !(type "javac" > /dev/null 2>&1); then
    printf "Please install Maven first !\n"
    exit
fi
mvn -v

printf "\n"

printf "3. Checking Git...\n"
if !(type "git" > /dev/null 2>&1); then
    printf "Please install Git first !\n"
    exit
fi
git --version
git_user_name=`git config user.name`
printf "USERNAME = %s\n" $git_user_name
git_user_email=`git config user.email`
printf "EMAIL    = %s\n" $git_user_email
if [ -z $git_user_name ] || [ -z $git_user_email ]; then
    printf "Git user name or email is not set, please set 'git config user.name USERNAME' & 'git config user.email EMAIL' !\n"
    exit
fi

printf "\n"

printf "4. Building...\n"
if [ -e ./keystore ]; then
    mvn clean compile install --settings ./.mvn/local-settings.xml
else
    mvn clean compile install jarsigner:sign -Djarsigner.skip=true
fi
ret=$?
if [ $ret -ne 0 ]; then
    printf "BUILD ERROR !\n"
    exit $ret
fi

printf "\n"

printf "5. Copy Application Files...\n"
if [ -e ./src/NoraGateway/target ]; then
    cp ./src/NoraGateway/target/*.zip ./dist/
fi
if [ -e ./src/NoraDStarProxyGateway/target ]; then
    cp ./src/NoraDStarProxyGateway/target/*.zip ./dist/
fi
if [ -e ./src/NoraExternalConnector/target ]; then
    cp ./src/NoraExternalConnector/target/*.zip ./dist/
fi
if [ -e ./src/ircDDBServer/target ]; then
    cp ./src/ircDDBServer/target/*.jar ./dist/
fi
if [ -e ./src/NoraHelper/target ]; then
    cp ./src/NoraHelper/target/*.jar ./dist/
fi
if [ -e ./src/NoraUpdater/target ]; then
    cp ./src/NoraUpdater/target/*.jar ./dist/
fi
if [ -e ./src/KdkAPI/target ]; then
    cp ./src/KdkAPI/target/*.zip ./dist/
fi

printf "\n"

printf "6. Clean cache files..."
mvn clean

printf " __________________________________________________\n"
printf "|                                                  \n"
printf "| Build complete !                                 \n"
printf "|__________________________________________________\n"

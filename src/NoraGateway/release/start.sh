#!/bin/bash

USE_JMX=false
JMX_PORT=9999


WORK_DIR=$(cd $(dirname $0);pwd)
cd $WORK_DIR

WEBAUTHCONFIG="${WORK_DIR}/config/WebRemoteControlUsers.xml"
if [ ! -e $WEBAUTHCONFIG ]; then
    cp -f $WEBAUTHCONFIG.default $WEBAUTHCONFIG
fi

CMD="sudo java"
if $USE_JMX ; then
    CMD="${CMD} -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=${JMX_PORT} -Dcom.sun.management.jmxremote.rmi.port=${JMX_PORT} -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=*"
fi
CMD="${CMD} -cp ./NoraHelper*.jar org.jp.illg.nora.helper.NoraHelper & java -cp ./NoraGateway*.jar org.jp.illg.nora.NoraGateway $*";

if [ -e ./NoraNanMonOLED ]; then
    CMD=$CMD"& sudo ./NoraNanMonOLED r1 cpu t2.0"
fi

if [ -e ./NoraNanMonTime ]; then
    CMD=$CMD"& sudo ./NoraNanMonTime cpu"

    if [ -e /dev/ttySC0 ]; then
        if [ ! -e /dev/ttyUSB0 ]; then
            sudo ln -s /dev/ttySC0 /dev/ttyUSB0
            sudo chown -h root:dialout /dev/ttyUSB0
        fi
    else
       CMD=$CMD mmdvm
    fi
fi

echo $CMD
eval $CMD


#!/bin/bash

TARGETDIR="/boot"
EXECUSER="$USER"

PROGNAME=$(basename $0)

Usage="$PROGNAME [-d|--directory ZipDirectory] [NoraGatewayArguments...]"

declare -a param=()

for OPT in "$@"
do
    case "$OPT" in
        '-h'|'--help' )
            echo "$Usage"
            exit 1
            ;;
        '-d'|'--directory' )
            if [[ -z "$2" ]] || [[ "$2" =~ ^-+ ]]; then
                echo "$PROGNAME: option requires an argument -- $1" 1>&2
                exit 1
            fi
            TARGETDIR="$2"
            shift 2
            ;;
        '--'|'-' )
            shift 1
            param+=( "$@" )
            break
            ;;
        -*)
            param+=( "$1" )
            shift 1
            ;;
        *)
            if [[ ! -z "$1" ]] && [[ ! "$1" =~ ^-+ ]]; then
                param+=( "$1" )
                shift 1
            fi
            ;;
    esac
done

echo "TARGETDIR=$TARGETDIR"
echo "NORAARGUMENTS=${param[@]}"
NORAARGUMENTS="${param[@]}"


WORK_DIR=$(cd $(dirname $0);pwd)
cd $WORK_DIR

NORAZIP=`ls -1tr ${TARGETDIR}/NoraGateway_v*.zip | head -1`



if [ ${NORAZIP} ]; then
    echo "Found NoraGateway zip file... ${NORAZIP}"
    echo "    Start auto update...${NORAZIP}"

    sudo ./update.sh -u ${EXECUSER} ${NORAZIP}

    sudo rm -f ${NORAZIP}
fi

NORACONFIG="${TARGETDIR}/NoraGateway.xml"
if [ -e $NORACONFIG ]; then
    echo "Copy config file...$NORACONFIG"

    cp -f $NORACONFIG ./config/
fi

NORAHOST="${TARGETDIR}/hosts.txt"
if [ -e $NORAHOST ]; then
    echo "Copy host file...$NORAHOST"

    cp -f $NORAHOST ./config/
fi

echo "Start NoraGateway with arguments...$NORAARGUMENTS"
./start.sh ${NORAARGUMENTS}


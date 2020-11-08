#!/bin/bash

WORK_DIR=$(cd $(dirname $0);pwd)
cd $WORK_DIR

wget --no-check-certificate -O ./config/hosts.txt https://kdk.ddns.net/norahosts/hosts.txt

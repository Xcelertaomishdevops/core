#!/bin/bash

# If the folder pre-exist, this should be existing unless a fresh deployment. Delete the workspace and freshly install 
if [ -d /home/ubuntu/workspace/xceler-ctrm-core ]; then
    rm -rf /home/ubuntu/workspace/xceler-ctrm-core
fi

sudo killall java
exit 0

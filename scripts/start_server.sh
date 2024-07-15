#!/bin/bash
mkdir -p /home/ubuntu/workspace/xceler-ctrm-core/logs
chmod -R 777 /home/ubuntu/workspace/xceler-ctrm-core/logs
chmod -R 777 /home/ubuntu/workspace/xceler-ctrm-core/launcher-0.0.1-SNAPSHOT.jar

if [ "$DEPLOYMENT_GROUP_NAME" == "xceler_ctrm_core_deploy_uat_deploy_grp" ]; then
    CURRENT_ENVIRONMENT="uat"
elif [ "$DEPLOYMENT_GROUP_NAME" == "xceler_ctrm_core_deploy_demo_deploy_grp" ]; then
    CURRENT_ENVIRONMENT="prod"
elif [ "$DEPLOYMENT_GROUP_NAME" == "xceler_ctrm_core_dev_deploy_grp" ]; then
    CURRENT_ENVIRONMENT="develop"
elif [ "$DEPLOYMENT_GROUP_NAME" == "xceler_ctrm_core_uat_becrux_deploy_group" ]; then
    CURRENT_ENVIRONMENT="becrux_uat"    
elif [ "$DEPLOYMENT_GROUP_NAME" == "xceler_ctrm_core_dev_temp_code_refractor_deploy_grp" ]; then
    CURRENT_ENVIRONMENT="develop"
else 
    CURRENT_ENVIRONMENT="local"
fi

echo "The current java profile is ${CURRENT_ENVIRONMENT}"
#######
##Initializing the rabbitmq user with credentials start
## As AMI will not create the user, creating the user exlicitly.
#######

SERVICE="rabbitmq-server"
if pgrep -x "$SERVICE" >/dev/null
then
    echo "$SERVICE is running"
    echo "Adding the admin user"
	sudo rabbitmq-plugins enable rabbitmq_management
    sudo rabbitmqctl add_user admin 'admin@ctrm!'
    sudo rabbitmqctl set_user_tags admin administrator
    sudo rabbitmqctl set_permissions -p / admin ".*" ".*" ".*"
else
    echo "$SERVICE stopped or not running"
    sudo service rabbitmq-server restart
	sudo rabbitmq-plugins enable rabbitmq_management
	sudo rabbitmqctl add_user admin 'admin@ctrm!'
    sudo rabbitmqctl set_user_tags admin administrator
    sudo rabbitmqctl set_permissions -p / admin ".*" ".*" ".*"
fi

### Loading Rabbitmq config json
sudo rabbitmqadmin import /home/ubuntu/workspace/xceler-ctrm-core/scripts/xceler_ctrm_rabbit.definitions.json
echo "Xceler Rabbitmq Config successfully loaded from: ${PWD}"

#######
## Rabbitmq config ends
#######

#######
## Start Xceler CTRM Server
#######
echo "Start Xceler CTRM Server: ${PWD}"
java -Dspring.profiles.active=${CURRENT_ENVIRONMENT} -jar /home/ubuntu/workspace/xceler-ctrm-core/launcher-0.0.1-SNAPSHOT.jar --springdoc.api-docs.enabled=false --springdoc.swagger-ui.enabled=false > /dev/null 2> /dev/null < /dev/null &
echo "Xceler CTRM Server started successfully"



> /dev/null 2> /dev/null < /dev/null &
#######
## Start Xceler CTRM Server
#######

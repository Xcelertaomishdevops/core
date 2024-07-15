#!/bin/bash -ex
region=`curl http://169.254.169.254/latest/dynamic/instance-identity/document|grep region|awk -F\" '{print $4}'`
echo $region
# Install the AWS CodeDeploy Agent.
cd /home/ec2-user/
wget https://aws-codedeploy-${region}.s3.amazonaws.com/latest/codedeploy-agent.noarch.rpm
yum -y install codedeploy-agent.noarch.rpm
# Install the Amazon CloudWatch Logs Agent.
wget https://s3.amazonaws.com/aws-cloudwatch/downloads/latest/awslogs-agent-setup.py
wget https://s3.amazonaws.com/aws-codedeploy-us-east-1/cloudwatch/codedeploy_logs.conf
wget https://s3.amazonaws.com/aws-codedeploy-us-east-1/cloudwatch/awslogs.conf
chmod +x ./awslogs-agent-setup.py
python awslogs-agent-setup.py -n -r ${region} -c ./awslogs.conf
mkdir -p /var/awslogs/etc/config
cp codedeploy_logs.conf /var/awslogs/etc/config/
service awslogs restart
# install updates
yum update -y
# install java 8
yum install java-1.8.0 -y
# remove java 1.7
yum remove java-1.7.0-openjdk -y
yum -y install mysql-server
/sbin/service mysqld start
# install httpd
yum -y install httpd
#/etc/init.d/httpd start
#Take original conf backup and add 8080 forwarding
mkdir /home/ec2-user/backup
cp /etc/httpd/conf/httpd.conf /home/ec2-user/backup/httpd.conf.original
VHOSTSFILE="/etc/httpd/conf/httpd.conf"
echo "<VirtualHost *:80>" >> $VHOSTSFILE
echo -e "\tProxyRequests Off" >> $VHOSTSFILE
echo -e "\tProxyPass / http://localhost:8080/" >> $VHOSTSFILE
echo -e "\tProxyPassReverse / http://localhost:8080/" >> $VHOSTSFILE
echo '</VirtualHost>' >> $VHOSTSFILE
#start httpd
/etc/init.d/httpd start

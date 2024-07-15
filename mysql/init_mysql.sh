#!/bin/bash
#DATABASES=$(mysql --user=root --password="" -e 'show databases')
DATABASE=ctrm_dev
DB_ADMIN=admin
DB_ADMIN_PASSWORD=Administrator@ctrm123
DB_ROOT_PASSWORD=admin123
DATABASES=$(mysql --user=root --password=$DB_ROOT_PASSWORD -e 'show databases')
DB_EXISTS=false
echo ${DATABASES}
for db in ${DATABASES}
do
  if [ "$db" == "$DATABASE" ]; then
    DB_EXISTS=true
  fi
done
if [ "$DB_EXISTS" == true ]
then
  echo "${DATABASE} exists"
else
  echo "${DATABASE} doesnt exist, creating new database"
  $(mysql --user=root --password=$DB_ROOT_PASSWORD -e "create database $DATABASE")
fi
USERS=$(mysql --user=root --password=$DB_ROOT_PASSWORD -e "select User from mysql.user")
DB_ADMIN_EXISTS=false
for user in ${USERS}
do
  if [ "$user" == "$DB_ADMIN" ]; then
    DB_ADMIN_EXISTS=true
  fi
done
if [ "$DB_ADMIN_EXISTS" == true ]
then
  echo "${DB_ADMIN} exists"
else
  echo "${DB_ADMIN} does not exist, creating new user"
  $(mysql --user=root --password=$DB_ROOT_PASSWORD -e "create user ${DB_ADMIN}@localhost identified by '${DB_ADMIN_PASSWORD}'")
fi
$(mysql --user=root --password=$DB_ROOT_PASSWORD -e "grant all privileges on *.* to ${DB_ADMIN}@localhost")
$(mysql --user=root --password=$DB_ROOT_PASSWORD -e "flush privileges")
echo "script executed succesfully"

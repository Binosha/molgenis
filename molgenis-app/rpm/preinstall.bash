#!/bin/bash

echo "########################################"
echo "PREINSTALL - MOLGENIS - started"
echo "########################################"
echo "[INFO] Cleanup old packages"
rm -rf /usr/local/share/molgenis/war/*.war
echo "[INFO] Add new user molgenis (in group: molgenis)"
if [[ $(getent passwd molgenis) ]]
then
  echo "[WARN] User 'molgenis' already exists"
else
  groupadd molgenis
  adduser molgenis -g molgenis --create-home
fi

TOMCAT_INSTALLED==$(rpm -qa | grep tomcat)
if [[ ! -z ${TOMCAT_INSTALLED} ]]
then
  echo "[INFO] Tomcat is installed"
  echo "[INFO] Add tomcat-user to molgenis-group to be able to write in MOLGENIS-homedir"
  usermod -g molgenis tomcat
fi

echo "----------------------------------------"
echo "PREINSTALL - MOLGENIS - finished"
echo "########################################"

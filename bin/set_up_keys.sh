#!/bin/bash

# scp checkit ling572_00@dryas:.ssh

# ssh-copy-id -i checkit.pub ling572_00@dryas

for a in ling572_01 ling572_02 ling572_03 ling572_04 ling572_05 ling572_06 ling572_07 ling572_08 ling572_09 ; do
   sudo -u $a mkdir /home/$a/.ssh ;
   sudo -u $a cp -R /home/ling572_00/authorized_keys /home/$a/.ssh ;
done

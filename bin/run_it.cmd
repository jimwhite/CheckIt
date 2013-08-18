####################
#
# James White UW NetID jimwhite
# LING473 Sample Project
#
####################

Universe   = vanilla

Environment = PATH=/home2/jimwhite/Projects/Groovy/groovy-2.1.6/bin:/usr/kerberos/bin:/usr/local/bin:/bin:/usr/bin:/opt/git/bin:/opt/scripts:/condor/bin;LD_LIBRARY_PATH=/opt/xerces/lib

Executable  = $(CHECKIT_HOME)/bin/$(_PROJECT_ID).groovy
Arguments   = run_it $(_PROJECT_ID)
Log         = run_it_log.txt
Input       = $(_PROJECT_TAR_FILE)
Output      = run_it_out.html
Error	    = run_it_err.txt
Notification=Error
Queue

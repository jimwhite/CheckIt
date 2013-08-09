####################
#
# James White UW NetID jimwhite
# LING473 Sample Project
#
####################

Universe   = vanilla

Environment = PATH=/home2/jimwhite/Projects/Groovy/groovy-2.1.6/bin:/opt/mono/bin:/opt/emacs22/bin:/opt/perl/bin:/usr/kerberos/bin:/usr/local/bin:/bin:/usr/bin:/opt/git/bin:/opt/scripts:/condor/bin:/opt/cluto-2.1.1/Linux:/opt/TIGERSearch/bin:/opt/xerox-tools:/opt/JMX:/opt/ANT/bin:/opt/javacc-4.0/bin:/opt/lucene-1.9.1/src:/opt/jwnl13rc3/bin:/opt/tnt:/opt/python-modules/bin:/opt/katoob/bin:/NLP_TOOLS/parsers/stanford_parser/latest:/opt/apache-maven/bin;LD_LIBRARY_PATH=/NLP_TOOLS/tool_sets/xle/latest/lib:/opt/xerces/lib:/opt/python-2.6/lib:/opt/libedit/lib

Executable  = run.sh
Arguments   = 20
Log         = project0.log
Output      = outputX.txt
Error	    = project0.err
Notification=Error
Queue

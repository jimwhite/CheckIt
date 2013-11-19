#!/bin/sh

CHECKIT_HOME=/home/ling572_00/Projects/CheckIt
export GROOVY_HOME=/home/jimwhite/Projects/Groovy/groovy-2.1.6
export PATH=$CHECKIT_HOME/bin:$GROOVY_HOME/bin:$PATH

# which groovy
# groovy --version

# An exception from ling570_hw6.groovy on stderr complaining about about a "Broken pipe" is OK.
# Exception in thread "Thread-2" org.codehaus.groovy.runtime.InvokerInvocationException: java.io.IOException: Broken pipe

ling570_hw6.groovy $*

tidy_hmm.groovy <checker/2g_hmm >checker/tidy_2g_hmm
diff_hmm.groovy ~/l570/hw6/tidy_wsj.2g_hmm checker/tidy_2g_hmm >checker/diff_wsj_2g_hmm.txt

tidy_hmm.groovy <checker/3g_hmm_118 >checker/tidy_3g_hmm_118
diff_hmm.groovy ~/l570/hw6/tidy_wsj.3g_hmm_0.1_0.1_0.8 checker/tidy_3g_hmm_118 >checker/diff_wsj_3g_hmm_118.txt
diff_hmm.groovy ~/l570/hw6/tidy_jim.3g_hmm_0.1_0.1_0.8 checker/tidy_3g_hmm_118 >checker/diff_jim_3g_hmm_118.txt


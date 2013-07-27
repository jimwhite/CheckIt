#!/usr/bin/perl -wU
delete @ENV{keys %ENV};
$ENV{'PATH'} = '/bin:/usr/bin';
exec '/home2/jimwhite/Projects/Groovy/groovy-2.1.6/bin/groovy', '/home2/ling572_00/CheckIt/bin/check_it.groovy', 'ls ; env'

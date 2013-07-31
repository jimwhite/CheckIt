#!/usr/bin/env /home2/jimwhite/Projects/Groovy/groovy-2.1.6/bin/groovy

println args

println System.getProperty('user.dir')

if (args.size() > 1) {
   println new File(args[1]).exists()
}

println '---'
println System.in.text

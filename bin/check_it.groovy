#!/usr/bin/env -i PATH=/usr/bin:/bin /home2/jimwhite/Projects/Groovy/groovy-2.1.6/bin/groovy

//System.getenv().each { println it }

//System.properties.each { println it }

data_dir = new File("/home2/jimwhite/Projects/CheckIt")
//data_dir = new File("/home2/ling572_00/Projects/CheckIt")

lock_file = new RandomAccessFile(new File(data_dir, "lock.file"), "rw")

lock = null

println "Howdy!"

println data_dir.absolutePath

if (get_lock()) {
    println "Got It!"

    20.times { print "." ; sleep(1000) }

    lock.release()

    println " Done!"
} else {
    println "Don't got it!"
}

def get_lock()
{
    def attempts = 3

    while (lock == null && (lock = lock_file.channel.tryLock()) == null && attempts-- > 0) {
        println "Lock busy.  Will retry..."
        sleep(1000)
    }

    lock
}

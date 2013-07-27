#!/usr/bin/env /home2/jimwhite/Projects/Groovy/groovy-2.1.6/bin/groovy

println (["id"].execute().text)

// println (new File("/home2/ling572_00/.ssh/checkit").text)

System.getenv().each { println it }

System.properties.each { println it }

slave = null

try {
    slave = get_slave()

    if (slave == null) {
        println "All slaves are busy.  Please try again later."
    } else {
        println "Got One!"
        use_slave(slave, args)
        println "Done!"
    }
} finally {
    if (slave != null) slave.lock.release()
}

def get_slave()
{
    File data_dir = new File("/home2/ling572_00/CheckIt")

    for (slave_number in 1..9) {
        String slave_id = "ling572_0$slave_number"
        def slave_home = new File("/home2", slave_id)
        def lock_file = new RandomAccessFile(new File(data_dir, slave_id + ".lock"), "rw")
        def lock = lock_file.channel.tryLock()
        if (lock != null) {
            return [id:slave_id, home:slave_home, lock:lock]
        }
    }

    return null
}

def use_slave(slave, args)
{
//    def identity_file = new File(System.getProperty("user.home"), ".ssh/checkit")
    def identity_file = new File("/home2/ling572_00/.ssh/checkit")
    def command = ["ssh", "-i", identity_file, slave.id + "@patas.ling.washington.edu", *args]

    def done = false

    def proc = command.execute()

//    proc.withWriter { stdin ->
//       def c
//       while (!done && (c = System.in.read()) >= 0) {
//           stdin.write(c)
//       }
//    }

    proc.consumeProcessOutput(System.out, System.err)
    proc.waitFor()

    done = true

    println()
    println "exitValue = ${proc.exitValue()}"
}

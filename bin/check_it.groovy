#!/usr/bin/env /home2/jimwhite/Projects/Groovy/groovy-2.1.6/bin/groovy

//println (["id"].execute().text)

// println (new File("/home2/ling572_00/.ssh/checkit").text)

//System.getenv().each { println it }

//System.properties.each { println it }

check_it_home = new File("/home2/ling572_00/Projects/CheckIt/")

check_it_binaries = new File(check_it_home, "bin")

runners = ["project0":"project0.groovy", "project1":"project1.groovy"]

slave = null

try {
    if (args.size() < 1) {
        println "Usage: check_it <project_id>"
    } else {
       slave = get_slave()

       if (slave == null) {
          println "All slaves are busy.  Please try again later."
       } else {
	//        println "Got One!"

          def runner = runners[args[0]]
          if (runner == null) {
             println "Unknown project id : ${args[0]}"
          } else {
             def runner_exe = new File(check_it_binaries, runner)
  
             use_slave(slave, [runner_exe.absolutePath, *args])
          }
	//        println "Done!"
       }
    }
} finally {
    if (slave != null) slave.lock.release()
}

def get_slave()
{
    File data_dir = new File(check_it_home, "data")

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

    proc.withOutputStream { stdin ->
       byte[] buf = new byte[100000]
       def cnt
       while ((cnt = System.in.read(buf, 0, buf.size())) >= 0) {
	  stdin.write(buf, 0, cnt)
       }
    }

    proc.consumeProcessOutput(System.out, System.err)
    proc.waitFor()

    done = true

    println()
    println "exitValue = ${proc.exitValue()}"
}

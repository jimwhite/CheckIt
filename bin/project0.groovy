#!/usr/bin/env /home2/jimwhite/Projects/Groovy/groovy-2.1.6/bin/groovy

println args

println System.getProperty('user.dir')

def checkit_dir = new File('project0')

def tar_file = File.createTempFile('sub', '', checkit_dir)

def copy_total = 0

try {
    tar_file.withOutputStream { output ->
        System.in.withStream { InputStream input ->
            byte[] buf = new byte[100000]
            def cnt
            while ((cnt = input.read(buf, 0, buf.size())) >= 0) {
                output.write(buf, 0, cnt)
                copy_total += cnt
            }
        }
    }

    println "Copied $copy_total bytes successfully."
} catch(Exception ex) {
    println "Copying input (tar) file failed after ${copy_total} bytes: ${ex.message}"
}

def temp_dir = new File(checkit_dir, tar_file.name + '.dir')

temp_dir.mkdir()


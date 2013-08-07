#!/usr/bin/env /home2/jimwhite/Projects/Groovy/groovy-1.8.6/bin/groovy

import groovy.xml.MarkupBuilder

import java.util.regex.Pattern

//environment = System.getenv().entrySet().grep { it.key =~ /PATH/ }.collect { it.key + '=' + it.value }
environment = System.getenv().entrySet().collect { it.key + '=' + it.value }

def checkit_dir = new File('project0')

def tar_file = File.createTempFile('sub', '', checkit_dir)

def temp_dir = new File(checkit_dir, tar_file.name + '.dir')

temp_dir.mkdirs()

def report_file = new File(temp_dir, 'report.html')

report_file.withWriter {
    new MarkupBuilder(it).html {
        def project_id = args.size() > 0 ? args[0] : "MISSING!"

        h1 "CheckIt! ${project_id}"

        p(args as List)

        p System.getProperty('user.dir')

        copy_input_to_tar_file(tar_file, delegate)

        def content_dir = unpack_it(tar_file, temp_dir, delegate)

        if (content_dir) {
            h2 "Contents"
            table {
                tr { th 'Name' ; th(style:'text-align:right', 'Size') }
                content_dir.eachFile { file ->
                    tr {
                        td(style:'font-family:sans-serif',  file.name)
                        td(style:'text-align:right', file.size())
                    }
                }
            }

            def inventory = take_inventory(content_dir)

            h2 "Submission Inventory"
            table {
                tr { th 'Item' ; th 'Present?' ; th 'OK?' ; th 'Pattern' ; th 'Full Path' /*; th(style:'text-align:right', 'Size')*/ }
                inventory.each { item ->
                    tr {
                        td item.name
                        td(item.exists ? 'yes' : 'no')
                        td(item.exists ? 'ok' : (item.required ? 'NO' : 'ok?'))
                        td(style:'font-family:sans-serif',  item.path)
                        td(style:'font-family:sans-serif',  item.actual_path)
//                        td(style:'text-align:right', item.size)
                    }
                }
            }

            if (inventory.ok.every()) {
                inventory = inventory.collectEntries { [it.name, it] }
                def executable = inventory.Exec
                if (!executable.file.canExecute()) {
                    h2 "Executable ${executable.path} is not executable"
                } else {
                    h2 "Ready To Run Condor Job"
                }
            }

        } else {
            h2 "No Content Found!"
        }
    }
}

def condor_get_variable(File condor_job, variable_name)
{
    condor_get_variable(condor_job.readLines(), variable_name)
}

def condor_get_variable(List<String> condor_job, variable_name)
{
    def pattern = ~"(?i)^\\s*$variable_name\\s*=\\s*(.*)\$"
    def values = condor_job.grep(pattern)
    values.size() < 1 ? null : (values.head() =~ pattern)[0][1]
}

def take_inventory(File content_dir)
{
   [
        check_item(name:'Exec', path:'run.sh', required:true, dir:content_dir)
      , check_item(name:'Condor', path:'condor.cmd', required:true, dir:content_dir)
      , check_item(name:'Compile', path:'compile.sh', required:false, dir:content_dir)
      , check_item(name:'Output', path:'output.txt', required:false, dir:content_dir)
      , check_item(name:'README', path:~'(?i)readme\\.(txt|pdf)', required:false, dir:content_dir)
   ]
}

def check_item(Map spec)
{
    File dir = spec.dir

    File file = null

    if (spec.path instanceof Pattern) {
        def files = dir.listFiles({ File ff_dir, String name -> spec.path.matcher(name).matches() } as FilenameFilter)
        if (files.length > 0) file = files[0]
    } else {
        file = new File(dir, spec.path)
    }

    def actual_path = ''
    def exists = false
    def size = -1

    if (file != null) {
        try {
            exists = file.exists()
            if (exists) {
                actual_path = file.path
                size = file.size()
            }
        } catch (IOException ex) {

        }
    }

    [name:spec.name, required:spec.required, file:file, exists:exists, ok:exists || !spec.required, path:spec.path, actual_path:actual_path, size:size]
}

def copy_input_to_tar_file(File tar_file, def html)
{
    def copy_total = 0

    try {
        tar_file.withOutputStream { output ->
            byte[] buf = new byte[100000]
            def cnt
            while ((cnt = System.in.read(buf, 0, buf.size())) >= 0) {
                output.write(buf, 0, cnt)
                copy_total += cnt
            }
        }
        html.p "Copied $copy_total bytes successfully."
    } catch(Exception ex) {
        html.p "Copying input (tar) file failed after ${copy_total} bytes: ${ex.message}"
    }
}

File unpack_it(File tar_file, File temp_dir, def html)
{
    html.h2 'Environment'
    environment.each { html.pre it }

    def unpack_dir = new File(temp_dir, 'unpack')
    def content_dir = new File(temp_dir, 'content')

    unpack_dir.mkdirs()

    def command = ['tar', 'xf', tar_file.absolutePath]

    html.pre command.join(' ')

    def proc = command.execute(environment, unpack_dir)
    def stdout = new StringBuilder()
    def stderr = new StringBuilder()

    proc.consumeProcessOutput(stdout, stderr)
    proc.waitFor()

    if (stdout) println stdout
    if (stderr) {
        html.h3 "ERROR:"
        html.pre stderr
    }
    if (proc.exitValue()) { html.h3 "Error: ${proc.exitValue()}" }

    def files = unpack_dir.listFiles()

    // Find the top-level directory of the tar file.  Skip levels with just a single directory.
    while (files.size() == 1 && files[0].isDirectory()) {
        unpack_dir = files[0]
        files = unpack_dir.listFiles()
    }

    if (files.size() < 1) {
        html.h3 "Can't unpack tar file or it is empty!"
        null
    } else {
        // Move the top-level of the tar file to the 'content' directory.
        if (!unpack_dir.renameTo(content_dir)) {
            html.h3 "RENAME FAILED!"
        }
        content_dir
    }
}

System.out.withWriter { it.print report_file.text }

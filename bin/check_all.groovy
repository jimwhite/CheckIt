import groovy.xml.MarkupBuilder

// export CHECKIT_HOME=/home2/jimwhite/Projects/CheckIt

System.out.withWriter {
    new MarkupBuilder(it).html {
//        pre(System.properties.entrySet().join('\n'))
//        pre(System.getenv().entrySet().join('\n'))

        def environment = System.getenv().entrySet().collect { it.key + '=' + it.value }

        def checkit_dir = new File(System.getenv('CHECKIT_HOME'))
        def checkit_bin_dir = new File(checkit_dir, 'bin')

        def run_it_exe = new File(checkit_bin_dir, 'run_it.cmd')

        def run_it = { File working_dir, List<String> command ->
            p {
                h3 command.join(' ')
            }

            def exitValue

            def outFile = new File(working_dir, "submit_out.txt")
            def errFile = new File(working_dir, "submit_err.txt")

            outFile.withOutputStream { OutputStream stdout ->
                errFile.withOutputStream { OutputStream stderr ->
                    def proc = command.execute(environment, working_dir)
                    proc.consumeProcessOutput(stdout, stderr)
                    proc.waitFor()
                    exitValue = proc.exitValue()
                }
            }

            if (exitValue) {
                h3 'Error!'
                p "exitValue: $exitValue"
            }

            def outText = outFile.text
            if (outText) {
                pre outText
            }

            def errText = errFile.text
            if (errText) {
                h3 "stderr"
                pre errText
            }

            exitValue
        }

        def project_id = args.head()

        args.tail().each {
            def project_dir = new File(it)
            def project_tar_file = new File(project_dir, project_id + ".tar") // find_project_tar_file(project_dir)
            def command = ['condor_submit', run_it_exe
                    , "-append", "CHECKIT_HOME=$checkit_dir"
                    , "-append", "_PROJECT_ID=$project_id"
                    , "-append", "_PROJECT_TAR_FILE=${project_tar_file.absolutePath}"]
            run_it(project_dir, command)
        }
    }
}

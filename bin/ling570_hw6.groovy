#!/usr/bin/env /home2/jimwhite/Projects/Groovy/groovy-1.8.6/bin/groovy

import groovy.xml.MarkupBuilder

import java.util.regex.Pattern

//environment = System.getenv().entrySet().grep { it.key =~ /PATH/ }.collect { it.key + '=' + it.value }
environment = System.getenv().entrySet().collect { it.key + '=' + it.value }

def MAX_WAIT_SECONDS = 300

def submitter_id = "ling570"
def project_id = "hw6"

def home_dir = new File(System.getProperty('user.home'))

def gold_files_dir = new File(home_dir, 'l570/hw6')

def content_dir = new File(args[0])

def checkit_dir = new File('project1')

checkit_dir.mkdirs()

def tar_file = File.createTempFile(project_id + '_', '', new File('.'))

def temp_dir = new File(checkit_dir, tar_file.name + '.dir')

temp_dir.mkdirs()

def report_file = new File(temp_dir, 'report.html')

report_file.withWriter {
    new MarkupBuilder(it).html {
        h1 "CheckIt! ${project_id} for ${submitter_id}"

        p(args as List)

        p System.getProperty('user.dir')

        h2 'Environment'
        pre environment.join('\n')

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
            def inventory_by_name = inventory.collectEntries { [it.name, it] }

            def check_create_hmm = { String program_name, String output_name, File pos_in_file, List arguments ->
                def executable = inventory_by_name[program_name]
                if (!executable.file.canExecute()) {
                    h2 "Executable ${executable.path} is not executable"
                } else {
                    h2 "Running $program_name for $output_name"

                    def run_it = { List<String> command, File inFile, File outFile, File errFile ->
                        p {
                            pre command.join(' ')
                        }

                        def exitValue

                        outFile.withOutputStream { OutputStream stdout ->
                            errFile.withOutputStream { OutputStream stderr ->
                                def proc = command.execute(environment, content_dir)
                                proc.consumeProcessOutput(stdout, stderr)
                                proc.withWriter { it << inFile }
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
                            h3 'stdout'
                            pre outText
                        }

                        def errText = errFile.text
                        if (errText) {
                            h3 "stderr"
                            pre errText
                        }

                        exitValue
                    }

                    def show_text = { List<String> lines, Integer len ->
                        if (lines.size() > len) {
                            def mid = len / 2 as Integer
                            lines = lines[0..<mid] + ['', "... skipping ${lines.size() - len} lines ...", ''] + lines[(-mid)..-1]
                        }

                        if (lines.size() > 0) {
                            pre(lines.join('\n'))
                        } else {
                            p { em "Empty" }
                        }
                    }

                    //  final TOTAL_KEY = " <TOTAL> "
                    def epsilon = 1.0e-4

                    def load_section = { Map headers, SectionReader lm_file_reader, String name, String delimiter, Pattern pattern ->
                        def count = 0
                        def stateProbs = [:]   // .withDefault { [(TOTAL_KEY):0] }

                        def minLogRatio = null
                        def maxLogRatio = null

                        lm_file_reader.findSection('\\' + delimiter)

                        def line

                        while ((line = lm_file_reader.readLine()) != null) {
                            line = line.trim()
                            if (line) {
//                                def groups = (pattern.matcher(line))[0]
                                if (pattern.matcher(line).matches()) {
//                                    def (_, from_state, to_state, prob, logProb) = (pattern.matcher(line))[0]
//                                    logProb = logProb as Double
                                    def (_, from_state, to_state, prob) = (pattern.matcher(line))[0]
                                    prob = prob as BigDecimal

                                    if (!stateProbs.containsKey(from_state)) { stateProbs[from_state] = [:] }
                                    stateProbs[from_state][to_state] = prob
                                    // stateProbs[from_state][TOTAL_KEY] += prob
                                    count += 1

//                                    def logRatio = Math.log(prob) / logProb
//                                    if (!logRatio.isNaN()) {
//                                        if (minLogRatio == null || logRatio < minLogRatio)  minLogRatio = logRatio
//                                        if (logRatio > maxLogRatio)  maxLogRatio = logRatio
//                                    }
                                } else {
                                    h3 "Badly formatted line"
                                    pre line
                                }
                            }
                        }

//                        if (maxLogRatio && minLogRatio && ((maxLogRatio - minLogRatio) > epsilon)) {
//                            h3 "LogProb base has wide range $minLogRatio to $maxLogRatio"
//                        }

/*
                        stateProbs.each { String state, Number prob ->
                            if ((prob != 1) && ((prob < 1-epsilon) || (prob > 1+epsilon))) {
                                pre "Bad $delimiter probabilities: state $state: sum to ${String.format('%f', prob)}"
                            }
                        }
*/

                        if (headers[name] != count) {
                            pre "Mismatched header count for $name: header value ${headers[name]}: actual count $count"
                        }

                        stateProbs
                    }

                    def check_symbol_count = { Map headers, File hmm_model_file ->
                        Set stateSet = []
                        Set symbolSet = []

                        SectionReader lm_file_reader = new SectionReader(new FileReader(hmm_model_file))

                        def line

                        lm_file_reader.findSection('\\emission')

                        while ((line = lm_file_reader.readLine()) != null) {
                            line = line.trim()
                            if (line) {
                                def (_, state, symbol) = ( line =~ /^(\S+)\s+(\S+)/)[0]
                                stateSet.add(state)
                                symbolSet.add(symbol)
                            }
                        }

                        if (stateSet.size() != headers.state_num) {
                            pre "Mismatched header value: state_num ${headers.state_num}: actual count: ${stateSet.size()}"
                        }

                        if (symbolSet.size() != headers.sym_num) {
                            pre "Mismatched header value: sym_num ${headers.sym_num}: actual count: ${symbolSet.size()}"
                        }
                    }

                    def load_hmm = { File hmm_model_file ->
                        Map headers = [:]

                        // Load the author's language model.
                        SectionReader lm_file_reader = new SectionReader(new FileReader(hmm_model_file))

                        // There is no delimiter to introduce the header section
                        //lm_file_reader.findSection("\\header")
                        lm_file_reader.inSection = true

                        def line

                        while ((line = lm_file_reader.readLine()) != null) {
                            line = line.trim()
                            if (line) {
                                def (_, name, count) = (line =~ /^\s*([^=]+)=(\d+)/)[0]
                                headers[name] = count as Integer
                            }
                        }

                        p headers

                        def missingHeaders = ['state_num', 'sym_num', 'init_line_num', 'trans_line_num', 'emiss_line_num'] - headers.keySet()

                        missingHeaders.each {
                            pre "Missing header $it"
                        }

//                        def init_probs = load_section(headers, lm_file_reader, 'init_line_num', 'init', ~/^(\S++)(\s++)(\S++)\s++(\S++).*/)
//                        def trans_probs = load_section(headers, lm_file_reader, 'trans_line_num', 'transition', ~/^(\S++)\s++(\S++)\s++(\S++)\s++(\S++).*/)
//                        def emiss_probs = load_section(headers, lm_file_reader, 'emiss_line_num', 'emission', ~/^(\S++)\s++(\S++)\s++(\S++)\s++(\S++).*/)
                        def init_probs = load_section(headers, lm_file_reader, 'init_line_num', 'init', ~/^(\S++)(\s++)(\S++)\s*+.*/)
                        def trans_probs = load_section(headers, lm_file_reader, 'trans_line_num', 'transition', ~/^(\S++)\s++(\S++)\s++(\S++)\s*+.*/)
                        def emiss_probs = load_section(headers, lm_file_reader, 'emiss_line_num', 'emission', ~/^(\S++)\s++(\S++)\s++(\S++)\s*+.*/)

                        check_symbol_count(headers, hmm_model_file)

                        [headers:headers, init:init_probs, trans:trans_probs, emiss:emiss_probs]
                    }

                    def compare_hmm_probs = { String section_name, Map exp_probs, Map act_probs ->
                        def all_from_states = (exp_probs.keySet() + act_probs.keySet()).unique().sort()
                        table {
                            tr { tr { td("from") ; td("to") ; td("expected") ; td("actual") } }
                            all_from_states.each { from_state ->
                                if (exp_probs.containsKey(from_state) && act_probs.containsKey(from_state)) {
                                    def all_to_states = (exp_probs[from_state].keySet() + act_probs[from_state].keySet()).unique().sort()
                                    all_to_states.each { to_state ->
                                        if (exp_probs[from_state].containsKey(to_state) && act_probs[from_state].containsKey(to_state)) {
                                            if (Math.abs(act_probs[from_state][to_state] - exp_probs[from_state][to_state]) > epsilon) {
                                                tr {
                                                    td from_state
                                                    td to_state
                                                    td (exp_probs[from_state][to_state])
                                                    td (act_probs[from_state][to_state])
                                                }
                                            }
                                        } else {
                                            tr {
                                                td from_state
                                                td to_state
                                                td (exp_probs[from_state].containsKey(to_state) ? exp_probs[from_state][to_state] : '-missing-')
                                                td (act_probs[from_state].containsKey(to_state) ? act_probs[from_state][to_state] : '-missing-')
                                            }
                                        }
                                    }
                                } else {
                                    tr {
                                        td from_state
                                        td "-transitions-"
                                        td (exp_probs.containsKey(from_state) ? "${exp_probs[from_state].size()} states" : '-missing-')
                                        td (act_probs.containsKey(from_state) ? "${act_probs[from_state].size()} states" : '-missing-')
                                    }
                                }
                            }
                        }
                    }

                    def compare_hmm = { exp_hmm, act_hmm ->
                        ['trans', 'emiss'].each { section_name ->
                            h3 "$section_name probabilities"
                            compare_hmm_probs(section_name, exp_hmm[section_name], act_hmm[section_name])
                        }
                    }

                    def hmm_file = new File(temp_dir, output_name)
                    def create_bigram_out_file = new File(temp_dir, program_name + '_out.txt')
                    def create_bigram_err_file = new File(temp_dir, program_name + '_err.txt')
                    def create_bigram_result = run_it(
                            [inventory_by_name[program_name].actual_path /*- (content_dir.absolutePath + File.separator)*/
                             , hmm_file.absolutePath] + arguments
                            , pos_in_file, create_bigram_out_file, create_bigram_err_file)

                    if (inventory_by_name[output_name].gold) {
                        def gold_hmm_file = new File(gold_files_dir, inventory_by_name[output_name].gold)
                        def gold_hmm = load_hmm(gold_hmm_file)
                        def test_hmm = load_hmm(hmm_file)

                        pre "Gold model file       : ${gold_hmm_file.path}"
                        pre "Calculated model file : ${hmm_file.path}"

                        if (gold_hmm) {
                            if (test_hmm) {
                                h3 "Comparing gold to model from run"
                                compare_hmm(gold_hmm, test_hmm)

                                h3 "Comparing submission to model from run"
                                compare_hmm(gold_hmm, test_hmm)
                            } else {
                                h3 "COULDN'T LOAD MODEL FROM RUN"
                            }
                        } else {
                            h3 "COULDN'T LOAD GOLD MODEL!!!"
                        }
                    }
                }
            }

            if (inventory.ok.every()) {
//                def user_submission_output = new File(temp_dir, 'output.txt')
//                inventory.Output.file.renameTo(user_submission_output)
//                inventory.Output.file = user_submission_output

                def pos_in_file = new File("/dropbox/13-14/570/hw6/examples/wsj_sec0.word_pos")
                def unk_prob_file = new File("/dropbox/13-14/570/hw6/examples/unk_prob_sec22")

                check_create_hmm('create_bigram_hmm', 'bigram_hmm', pos_in_file, [])
//                check_create_hmm('create_trigram_hmm', 'trigram_hmm_118', pos_in_file, ['0.1', '0.1', '0.8', unk_prob_file.path])
//                check_create_hmm('create_trigram_hmm', 'trigram_hmm_235', pos_in_file, ['0.2', '0.3', '0.5', unk_prob_file.path])

            } else {
                h2 "Required file(s) Missing!"
                p """One or more of the files required in order to submit this job to Condor are missing."""
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
    def pattern = ~"(?i)^\\s*$variable_name\\s*=(.*)\$"
    def values = condor_job.grep(pattern)
    values.size() < 1 ? null : ((values.head() =~ pattern)[0][1]).trim()
}

def take_inventory(File content_dir)
{
    def output_dir = new File(content_dir, 'q4')
    [
        check_item(name:'create_bigram_hmm', path:'create_2gram_hmm.sh', required:true, dir:content_dir)
        , check_item(name:'create_trigram_hmm', path:'create_3gram_hmm.sh', required:true, dir:content_dir)
        , check_item(name:'check_hmm', path:'check_hmm.sh', required:true, dir:content_dir)
        , check_item(name:'trigram_hmm_118', path:~/(?i)3g_hmm_0\.1_0\.1_0\.8(\.txt)?/, gold:'wsj.3g_hmm_0.1_0.1_0.8', required:false, dir:output_dir)
        , check_item(name:'trigram_hmm_235', path:~/(?i)3g_hmm_0\.2_0\.3_0\.5(\.txt)?//*, gold:'wsj.3g_hmm_0.2_0.3_0.5'*/, required:false, dir:output_dir)
        , check_item(name:'bigram_hmm', path:~/(?i)2g_hmm(\.txt)?/, gold:'2g_hmm.txt', required:false, dir:output_dir)
        , check_item(name:'README', path:~/(?i)hw6\.(txt|pdf)/, required:false, dir:content_dir)
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
//    def executable = false

    if (file != null) {
        try {
            exists = file.exists()
            if (exists) {
                actual_path = file.absolutePath
                size = file.size()
//                executable = file.canExecute()
            }
        } catch (IOException ex) {

        }
    }

    [name:spec.name, required:spec.required, file:file, exists:exists
            , ok:exists || !spec.required, path:spec.path, gold:spec.gold
            , actual_path:actual_path, size:size /*, executable:executable*/]
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
    def unpack_dir = new File(temp_dir, 'unpack')

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

// A filter for BufferedReader that will detect when we enter and exit ARPA file sections.
class SectionReader extends BufferedReader
{
    boolean inSection
    String currentLine

    SectionReader(Reader reader)
    {
        super(reader)
    }

    // Skip lines until we find one that matches the given section header.
    // Throws an IOException if one isn't found before EOF.
    public void findSection(String section)
    {
        while (currentLine != section) {
            currentLine = super.readLine()?.trim()

            if (currentLine == null) {
                throw new IOException("Can't find start of section '$section'")
            }
        }

        inSection = true
    }

    // Read the next line in this section.
    // Return null if we've reached the end of the section or the end of the file.
    public String readLine()
    {
        if (inSection) {
            currentLine = super.readLine()?.trim()

            if (!currentLine?.startsWith("\\")) {
                return currentLine
            }

            inSection = false
        }

        return null
    }
}

#!/usr/bin/env groovy

import java.util.regex.Pattern

// diff_hmm.groovy hmm_1 hmm_2

def hmm_1_file = new File(args[0])

def hmm_2_file = new File(args[1])

def lines_to_show = 50

System.out.withWriter {
    it.withPrintWriter { printer ->
        diff_hmm_files(hmm_1_file, hmm_2_file, printer)
    }
}

void diff_hmm_files(File hmm_1_file, File hmm_2_file, PrintWriter printer)
{
    def hmm_1_summary = load_hmm_summary(hmm_1_file)
    def hmm_2_summary = load_hmm_summary(hmm_2_file)

    def hmm_1_reader = new SectionReader_diff_hmm(new FileReader(hmm_1_file))
    hmm_1_reader.inSection = true
    def header_lines_1 = hmm_1_reader.readLines()

    def hmm_2_reader = new SectionReader_diff_hmm(new FileReader(hmm_2_file))
    hmm_2_reader.inSection = true
    def header_lines_2 = hmm_2_reader.readLines()

    def mismatches = [1..(header_lines_1.size()), header_lines_1, header_lines_2].transpose().grep { it[1] != it[2] }

    if (mismatches) {
        printer.println "Header mismatches"
        mismatches.each { line_num, line_1, line_2 ->
            printer.println "$line_num\t$line_1\t|\t$line_2"
        }
        printer.println()
    }

    diff_section("init", hmm_1_summary, hmm_2_summary, hmm_1_reader, hmm_2_reader, printer) {
        String[] row_1, String[] row_2 ->
        if (tag_pairs_differ(row_1[0], row_2[0]) || probs_differ(row_1[1], row_2[1])) {
            printer.println "init\t${row_1.join('\t')}\t|\t${row_2.join('\t')}"
        }
    }

    diff_section("transition", hmm_1_summary, hmm_2_summary, hmm_1_reader, hmm_2_reader, printer) {
        String[] row_1, String[] row_2 ->
        if (tag_pairs_differ(row_1[0], row_2[0]) || states_differ(row_1[1], row_2[1]) || probs_differ(row_1[2], row_2[2])) {
            printer.println "trans\t${row_1[0..2].join('\t')}\t|\t${row_2[0..2].join('\t')}"
        }
    }

    diff_section("emission", hmm_1_summary, hmm_2_summary, hmm_1_reader, hmm_2_reader, printer) {
        String[] row_1, String[] row_2 ->
        if (tag_pairs_differ(row_1[0], row_2[0]) || states_differ(row_1[1], row_2[1]) || probs_differ(row_1[2], row_2[2])) {
            printer.println "emiss\t${row_1[0..2].join('\t')}\t|\t${row_2[0..2].join('\t')}"
        }
    }

}

void diff_section(String section_name
        , Map hmm_1_summary, Map hmm_2_summary
    , SectionReader_diff_hmm hmm_1_reader, SectionReader_diff_hmm hmm_2_reader
    , PrintWriter printer, Closure row_func)
{
    printer.println "=== $section_name ==="

    def section_1_summary = hmm_1_summary[section_name]
    def section_2_summary = hmm_2_summary[section_name]

    section_1_summary.with {
        printer.print "left\t${from_states.size()}\t${to_states.size()}\t${count}"
    }
    printer.print "\t|\t"
    section_2_summary.with {
        printer.print "right\t${from_states.size()}\t${to_states.size()}\t${count}"
    }
    printer.println()

//    [section_1_summary, section_2_summary].each {
//        it.with {
//            printer.println "$section_name\t${from_states.size()}\t${to_states.size()}\t${count}"
//        }
//    }

    hmm_1_reader.findSection('\\' + section_name)
    hmm_2_reader.findSection('\\' + section_name)

    def row_1 = next_row(hmm_1_reader)
    def row_2 = next_row(hmm_2_reader)

    while (row_1 != null || row_2 != null) {
        if (row_1 == null) {
            printer.println "$section_name\t  ---\t|\t${row_2.join('\t')}"
            row_2 = next_row(hmm_2_reader)
        } else if (row_2 == null) {
            printer.println "$section_name\t${row_1.join('\t')}\t|\t---"
            row_1 = next_row(hmm_1_reader)
        } else {
            row_func(row_1, row_2)

            row_1 = next_row(hmm_1_reader)
            row_2 = next_row(hmm_2_reader)
        }
    }
}

def tag_pairs_differ(String state_1, String state_2)
{
    if (state_1 != state_2) {
        def tags = state_1.split('_')
        def f = state_2.startsWith(tags[0]) && state_2.endsWith(tags[1])
        !f
    } else {
        false
    }
}

def states_differ(String state_1, String state_2)
{
    state_1 != state_2
}

def probs_differ(prob_1, prob_2)
{
    (Math.abs((prob_1 as Double) - (prob_2 as Double)) > 0.001)
}

def next_row(SectionReader_diff_hmm reader)
{
    def row = reader.readLine()

    while (row != null && !row.trim()) { row = reader.readLine() }

    if (row != null) {
//        row = row.split(/\s+/) as List
//        if (row.size() == 3) { row.add(1, '') }
//        if (row.size() == 4) { row.remove(3) }

        row = row.split(/\s+/)
    }

    row
}

def load_section(String delimiter, Map headers, SectionReader_diff_hmm lm_file_reader, Pattern pattern )
{
    def count = 0
    Set to_states = []
    Map from_states = [:]

    lm_file_reader.findSection('\\' + delimiter)

    def line

    while ((line = lm_file_reader.readLine()) != null) {
        line = line.trim()
        if (line) {
            if (pattern.matcher(line).matches()) {
                def (_, from_state, to_state, prob) = (pattern.matcher(line))[0]
                prob = prob as BigDecimal

                if (!from_states.containsKey(from_state)) { from_states[from_state] = [to_state_count:0, total_prob:0] }
                from_states[from_state].to_state_count += 1
                from_states[from_state].total_prob += prob
                to_states << to_state
                count += 1
            }
        }
    }

    [section_name:delimiter, from_states:from_states, to_states:to_states, count:count]
}

def load_hmm_summary(File hmm_file)
{
    Map headers = [:]

    // Load the author's language model.
    SectionReader_diff_hmm lm_file_reader = new SectionReader_diff_hmm(new FileReader(hmm_file))

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

    def init_summary = load_section('init', headers, lm_file_reader, ~/^(\S++)(\s++)(\S++).*/)
    def trans_summary = load_section('transition', headers, lm_file_reader,  ~/^(\S++)\s++(\S++)\s++(\S++).*/)
    def emiss_summary = load_section('emission', headers, lm_file_reader, ~/^(\S++)\s++(\S++)\s++(\S++).*/)

    [headers:headers, init:init_summary, transition:trans_summary, emission:emiss_summary]
}

// A filter for BufferedReader that will detect when we enter and exit ARPA file sections.
class SectionReader_diff_hmm extends BufferedReader
{
    boolean inSection
    String currentLine

    SectionReader_diff_hmm(Reader reader)
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

    public List<String> readLines()
    {
        def lines = []

        def line = readLine()

        while (line != null) {
            lines << line
            line = readLine()
        }

        lines
    }
}


#!/usr/bin/env groovy

// diff_hmm.groovy hmm_1 hmm_2

def hmm_1_file = new File(args[0])
def hmm_1_reader = new SectionReader_diff_hmm(new FileReader(hmm_1_file))
hmm_1_reader.inSection = true

def hmm_2_file = new File(args[1])
def hmm_2_reader = new SectionReader_diff_hmm(new FileReader(hmm_2_file))
hmm_2_reader.inSection = true

def lines_to_show = 50

System.out.withWriter {
    def printer = new PrintWriter(it)

    def header_lines_1 = hmm_1_reader.readLines()
    def header_lines_2 = hmm_2_reader.readLines()

    def mismatches = [1..(header_lines_1.size()), header_lines_1, header_lines_2].transpose().grep { it[1] != it[2]}

    if (mismatches) {
        printer.println "Header mismatches"
        mismatches.each { line_num, line_1, line_2 ->
            printer.println "$line_num\t$line_1\t|\t$line_2"
        }
        printer.println()
    }

    ["init", "transition", "emission"].each { section_name ->
        printer.println "=== $section_name ==="

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
                if (states_differ(row_1[0], row_2[0])
                        || states_differ(row_1[1], row_2[1])
                        || probs_differ(row_1[2], row_2[2]))
                {
                    printer.println "$section_name\t${row_1.join('\t')}\t|\t${row_2.join('\t')}"
                }

                row_1 = next_row(hmm_1_reader)
                row_2 = next_row(hmm_2_reader)
            }
        }

        printer.println()
    }
}

def states_differ(String state_1, String state_2)
{
    if (state_1 != state_2) {
        def tags = state_1.split('_')
        def f = state_2.startsWith(tags[0]) && state_2.endsWith(tags[1])
        !f
    } else {
        false
    }
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
        row = row.split(/\s+/) as List
        if (row.size() == 3) { row.add(1, '') }
        if (row.size() == 4) { row.remove(3) }
    }

    row
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


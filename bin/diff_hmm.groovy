#!/usr/bin/env groovy

@Grab(group='org.apache.commons', module='commons-lang3', version='3.1')

import org.apache.commons.lang3.StringUtils

import java.util.regex.Pattern

// diff_hmm.groovy hmm_1 hmm_2

def hmm_1_file = new File(args[0])

def hmm_2_file = new File(args[1])

//println setToSortedArray((10..25).collect { it as String } as Set, 5)

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

    printer.println " === end report ==="
}

void diff_section(String section_name
        , Map hmm_1_summary, Map hmm_2_summary
    , SectionReader_diff_hmm hmm_1_reader, SectionReader_diff_hmm hmm_2_reader
    , PrintWriter printer, Closure row_func)
{
    def epsilon = 1.0e-4

    printer.println "=== $section_name ==="

    def section_1_summary = hmm_1_summary[section_name]
    def section_2_summary = hmm_2_summary[section_name]

    printer.print section_name
    section_1_summary.with {
        printer.print " left from_states=${from_states.size()} to_states=${to_states.size()} probs=${count}"
    }
    printer.print "\t|"
    section_2_summary.with {
        printer.print " right from_states=${from_states.size()} to_states=${to_states.size()} probs=${count}"
    }
    printer.println()

//    [section_1_summary, section_2_summary].each {
//        it.with {
//            printer.println "$section_name\t${from_states.size()}\t${to_states.size()}\t${count}"
//        }
//    }

    hmm_1_reader.findSection('\\' + section_name)
    hmm_2_reader.findSection('\\' + section_name)

    // Get the alignment.
    def from_states_1 = setToSortedArray(section_1_summary.from_states.keySet())
    def from_states_2 = setToSortedArray(section_2_summary.from_states.keySet())
    def from_states_alignment = alignText(from_states_1, from_states_2)

//    def to_states_1 = setToSortedArray(section_1_summary.to_states)
//    def to_states_2 = setToSortedArray(section_2_summary.to_states)
//    def to_states_alignment = from_states_alignment
//
//    if (to_states_1 != from_states_1 || to_states_2 != from_states_2)
//        to_states_alignment = alignText(to_states_1, to_states_2)

/*
    // The length of the longest line in the source text.
    def w = from_states_1*.length().max()

    from_states_alignment.each { j, i ->
        // The indicies in the alignment are one-based.  Zero means the empty string.
        String s_j = j ? from_states_1[j-1] : "---"
        String t_i = i ? from_states_2[i-1] : "---"

        // Using a fixed width for the first (source) column.
        // System.out.printf("%60s %4.2f %s\n", s_j, normalizedStringEditDistance(s_j, t_i), t_i)
        // Better to use the length of the longest line in the source text.
        printer.printf("%${w}s %4.2f %s\n", s_j, normalizedStringEditDistance(s_j, t_i), t_i)
    }
*/
    def row_1 = next_row(hmm_1_reader)
    def row_2 = next_row(hmm_2_reader)

    from_states_alignment.each { from_state_index_1, from_state_index_2 ->
        // The indicies in the alignment are one-based.  Zero means the empty element.
        if (from_state_index_1 > 0 && from_state_index_2 > 0) {
            String from_state_1 = from_states_1[from_state_index_1 - 1]
            String from_state_2 = from_states_2[from_state_index_2 - 1]

            def from_state_info_1 = section_1_summary.from_states[from_state_1]
            def from_state_info_2 = section_2_summary.from_states[from_state_2]

            def total_prob_mismatch = Math.abs(from_state_info_1.total_prob - from_state_info_2.total_prob) > epsilon
            def to_state_count_mismatch = from_state_info_1.to_state_count != from_state_info_2.to_state_count
            if (total_prob_mismatch || to_state_count_mismatch)
            {
                printer.print "$section_name mismatch ${to_state_count_mismatch ? 'tsc' : '   '} ${total_prob_mismatch ? 'tp' : '  '} : "
                printer.printf("\t%17s %4d %8.6f\t| %s %4d %8.6f\n"
                        , from_state_1, from_state_info_1.to_state_count, from_state_info_1.total_prob
                        , from_state_2, from_state_info_2.to_state_count, from_state_info_2.total_prob)

                while (row_1 != null && row_1[0] != from_state_1) row_1 = next_row(hmm_1_reader)
                def probs_1 = [:]
                while (row_1 != null && row_1[0] == from_state_1) {
                    probs_1[row_1[1]] = row_1[2] as Double
                    row_1 = next_row(hmm_1_reader)
                }

                while (row_2 != null && row_2[0] != from_state_2) row_2 = next_row(hmm_2_reader)
                def probs_2 = [:]
                while (row_2 != null && row_2[0] == from_state_2) {
                    probs_2[row_2[1]] = row_2[2] as Double
                    row_2 = next_row(hmm_2_reader)
                }

                def to_states_1 = setToSortedArray(probs_1.keySet(), 4)
                def to_states_2 = setToSortedArray(probs_2.keySet(), 4)
                def to_states_alignment = alignText(to_states_1, to_states_2)

//                if (to_states_2.length == 0) {
//                    printer.println to_states_1
//                    printer.println to_states_2
//                    printer.println to_states_alignment
//
//                    printer.println "ick"
//                }

                to_states_alignment.each { to_state_index_1, to_state_index_2 ->
                    if (to_state_index_1 > 0 && to_state_index_2 > 0) {
                        def to_state_1 = to_states_1[to_state_index_1 - 1]
                        def to_state_2 = to_states_2[to_state_index_2 - 1]
                        printer.printf("\t%21s -> %-21s %8.6f\t| %21s -> %-21s %8.6f\n"
                                , from_state_1, to_state_1, probs_1[to_state_1]
                                , from_state_2, to_state_2, probs_2[to_state_2]
                        )
                    } else if (to_state_index_1 > 0) {
                        def to_state_1 = to_states_1[to_state_index_1 - 1]
                        printer.printf("\t%21s -> %-21s %8.6f\n"
                                , from_state_1, to_state_1, probs_1[to_state_1]
                        )
                    } else if (to_state_index_2 > 0) {
                        def to_state_2 = to_states_2[to_state_index_2 - 1]
                        printer.printf("\t%49s\t| %21s -> %-21s %8.6f\n"
                                , ""
                                , from_state_2, to_state_2, probs_2[to_state_2]
                        )

                    } else {
                        System.err.println "Invalid alignment pair [0, 0]"
                    }
                }

                printer.println()

//            while ((row_1 != null && row_1[0] == from_state_1) || (row_2 != null && row_2[0] == from_state_2)) {
//                if (row_1 == null) {
//                    printer.println "$section_name\t  ---\t|\t${row_2.join('\t')}"
//                    row_2 = next_row(hmm_2_reader)
//                } else if (row_2 == null) {
//                    printer.println "$section_name\t${row_1.join('\t')}\t|\t---"
//                    row_1 = next_row(hmm_1_reader)
//                } else {
//                    row_func(row_1, row_2)
//
//                    row_1 = next_row(hmm_1_reader)
//                    row_2 = next_row(hmm_2_reader)
//                }
//            }
            }

        } else if (from_state_index_1 > 0) {
            String from_state_1 = from_states_1[from_state_index_1 - 1]
            def from_state_info_1 = section_1_summary.from_states[from_state_1]

            printer.print "$section_name unmatched lhs : "
            printer.printf("\t%12s\t%4d\t%7.5f\n"
                    , from_state_1, from_state_info_1.to_state_count, from_state_info_1.total_prob)
        } else if (from_state_index_2 > 0) {
            String from_state_2 = from_states_2[from_state_index_2 - 1]
            def from_state_info_2 = section_2_summary.from_states[from_state_2]

            printer.print "$section_name unmatched rhs : "
            printer.printf("\t\t\t\t\t|%12s\t%4d\t%7.5f\n"
                    , from_state_2, from_state_info_2.to_state_count, from_state_info_2.total_prob)
        } else {
            System.err.println "Invalid alignment pair [0, 0]"
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

String[] next_row(SectionReader_diff_hmm reader)
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

static String[] setToSortedArray(Set s)
{
//    m.keySet().sort().toArray(new String[m.keySet().size()])
    String[] a = s.toArray(new String[s.size()])
    Arrays.sort(a)
    a
}

static String[] setToSortedArray(Set s, int max_part)
{
    String[] a = s.toArray(new String[s.size()])
    Arrays.sort(a)

    if (a.length > max_part * 3) {
        String[] t = a
        a = new String[max_part * 3]
        max_part.times {
            a[it] = t[it]
            a[it + max_part] = t[(int) ((t.length - max_part) / 2) + it]
            a[it + max_part + max_part] = t[t.length - (max_part - it)]
        }
    }

    a
}

/**
 * Compute the edit distances between a source of m elements and a target of n elements using dynamic programming.
 *
 * @param m the number of elements in the source.
 * @param n the number of elements in the target
 * @param copy_or_subst_cost a closure that takes two zero-based element indicies : source, target
 *                           and returns the cost of replacing that source element with that target element
 * @param insert_cost a closure that takes a zero-based target element index and returns the cost of inserting that target element
 * @param delete_cost a closure that takes a zero-based source element index and returns the cost of deleting that source element
 * @return a two-dimensional float array of m+1 rows and n+1 columns that contains the edit distance between the
 *         corresponding source (row) and target (column) elements in terms of one-based indicies.
 */
static float[][] editDistances(int m, int n
                               , Closure copy_or_subst_cost
                               , Closure insert_cost = { 1 }
                               , Closure delete_cost = { 1 })
{
    // Use float instead of double to save a bit of space
    // since we don't need the additional range or resolution.
    float[][] distance = new float[m+1][n+1]

    // The Integer.times(Closure) function calls the closure the given
    // number of times with an incrementing parameter that starts at zero.

    n.times { i -> distance[0][i+1] = distance[0][i] + insert_cost(i) }
    m.times { j -> distance[j+1][0] = distance[j][0] + delete_cost(j) }

    n.times { i ->
        m.times { j ->
            distance[j+1][i+1] = [distance[j+1][i] + insert_cost(i),
                    distance[j][i+1] + delete_cost(j),
                    distance[j][i] + copy_or_subst_cost(j, i)].min()
        }
    }

    distance
}

static float stringEditDistance(String s, String t) {
//    editDistances(s.length(), t.length(), { int j, int i ->
//        (s.charAt(j) == t.charAt(i)) ? 0 : 2
//    })[s.length()][t.length()]

    if (s == t) return 0

//    int m = s.length()
//    int n = t.length()
//
//    // Use float instead of double to save a bit of space
//    // since we don't need the additional range or resolution.
//    float[][] distance = new float[m+1][n+1]
//
//    // The Integer.times(Closure) function calls the closure the given
//    // number of times with an incrementing parameter that starts at zero.
//
//    m.times { j -> distance[j+1][0] = distance[j][0] + 1 }
//    n.times { i -> distance[0][i+1] = distance[0][i] + 1 }
//
//    n.times { i ->
//        m.times { j ->
//            distance[j+1][i+1] = [distance[j+1][i] + 1,
//                    distance[j][i+1] + 1,
//                    distance[j][i] + ((s.charAt(j) == t.charAt(i)) ? 0 : 2)].min()
//        }
//    }
//
//    distance[m][n]

    StringUtils.getLevenshteinDistance(s, t)
}

static float normalizedStringEditDistance(String s, String t) {
    int len = s.length() + t.length()
    len > 0 ? stringEditDistance(s, t) / len : 0
}

static float[][] textEditDistances(String[] s, String[] t) {
//    editDistances(s.length, t.length, { int j, int i ->
//        s[j].equals(t[i]) ? 0 : (0.5 + normalizedStringEditDistance(s[j], t[i]))
//    })

    int m = s.length
    int n = t.length

    // Use float instead of double to save a bit of space
    // since we don't need the additional range or resolution.
    float[][] distance = new float[m+1][n+1]

    // The Integer.times(Closure) function calls the closure the given
    // number of times with an incrementing parameter that starts at zero.

    m.times { j -> distance[j+1][0] = distance[j][0] + 1 }
    n.times { i -> distance[0][i+1] = distance[0][i] + 1 }

    n.times { i ->
        m.times { j ->
            distance[j+1][i+1] = [distance[j+1][i] + 1,
                    distance[j][i+1] + 1,
                    distance[j][i] + stringEditDistance(s[j], t[i])].min()
//                    distance[j][i] + (0.5 + normalizedStringEditDistance(s[j], t[i]))].min()
        }
    }

    distance
}

static float normalizedTextEditDistance(String[] s, String[] t)
{
    int len = s.length + t.length

    if (len < 1) return 0

    def d = textEditDistances(s, t)[s.length][t.length]

    return (d / len)
}

/**
 * Generate an alignment list for source and target given an edit distances array.
 *
 * @param distance Two-dimensional array of edit distances.
 * @return A list of pairs of one-based indicies into the source (first element) and target (second element).
 *         Zero means an empty element for an insert or delete.
 */
static List<List> align(float[][] distance)
{
    // A list of pairs of one-based indicies into the source (first element) and target (second element).
    def alignment = []

    // Start in the bottom right corner.
    // That is the cell for edit distance between the final source and target elements.
    int j = distance.length - 1;
    int i = distance[0].length - 1;

    // While we are at a distance between a source element and a target element...
    while (j > 0 && i > 0) {
        if ((distance[j-1][i-1] <= distance[j][i-1]) && (distance[j-1][i-1] <= distance[j-1][i])) {
            // The diagonal (j-1, i-1) cell has the lowest value, so we're doing a copy or substitution.
            alignment << [j--, i--]
        } else if (distance[j][i-1] <= distance[j-1][i]) {
            // The horizontal (j, i-1) cell has the lowest value, so we're doing an insertion.
            alignment << [0, i--]
        } else {
            // The vertical (j-1, i) cell has the lowest value, so we're doing a deletion.
            alignment << [j--, 0]
        }
    }

    // Our position will not yet be at the origin if the last move wasn't diagonal (copy-or-subst).
    // If our row is > 0 then we need to delete elements from the source.
    while (j > 0) alignment << [j--, 0]
    // If our column is > 0, then we need to insert elements from the target.
    while (i > 0) alignment << [0, i--]

    // The list of alignments is reversed from the order of the text, so put it in textual order.
    alignment.reverse()
}

static List<List> alignText(String[] s, String[] t)
{
    if (s == t) {
//        println "alignText shortcut!"
        (1..s.length).collect { [it, it] }
    } else {
        align(textEditDistances(s, t))
    }
}

/**
 * List<List> to a two-dimensional array for the benefit of tests written in Java.
 *
 * @param alignment A list of lists of integers.
 * @return a two-dimensional array, the inner array being int[]
 */
static Object[] alignmentToArray(List<List> alignment)
{
    alignment.collect { it.toArray(new int[it.size()]) }.toArray()
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


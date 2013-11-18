import java.util.regex.Pattern

System.out.withWriter {
    def hmm_printer = new PrintWriter(it)
    System.in.withReader { hmm_reader ->
        def hmm = load_hmm_safe(hmm_reader)
        if (hmm == null) {
            System.err.println "Error - cannot tidy hmm"
        } else {
            hmm.headers.keySet().sort().each { hmm_printer.println "$it=${hmm.headers[it]}"}
            hmm_printer.println()
            ["init", "transition", "emission"].each { write_hmm_section(it, hmm[it], hmm_printer) }
        }
    }
}

def write_hmm_section(String section_name, Map<String, Map> probs, PrintWriter hmm_printer)
{
    hmm_printer.println()
    hmm_printer.print '\\'
    hmm_printer.println section_name

    probs.keySet().sort().each { from_state ->
        def state_probs = probs[from_state]
        state_probs.keySet().sort().each { to_state ->
            def p = state_probs[to_state]
            if (to_state.trim())
                hmm_printer.println "$from_state\t$to_state\t${p}\t${Math.log10(p)}"
            else
                hmm_printer.println "$from_state\t${p}\t${Math.log10(p)}"
        }
    }
}

def load_section(Map headers, SectionReader_tidy_hmm lm_file_reader, String name, String delimiter, Pattern pattern )
{
    def count = 0
    def stateProbs = [:]   // .withDefault { [(TOTAL_KEY):0] }

    def minLogRatio = null
    def maxLogRatio = null

    lm_file_reader.findSection('\\' + delimiter)

    def line

    while ((line = lm_file_reader.readLine()) != null) {
        line = line.trim()
        if (line) {
            if (pattern.matcher(line).matches()) {
                def (_, from_state, to_state, prob) = (pattern.matcher(line))[0]
                prob = prob as BigDecimal

                if (!stateProbs.containsKey(from_state)) { stateProbs[from_state] = [:] }
                stateProbs[from_state][to_state] = prob
                count += 1
            }
        }
    }

    if (headers[name] != count) {
        System.err.println "Mismatched header count for $name: header value ${headers[name]}: actual count $count"
    }

    stateProbs
}

def load_hmm(Reader hmm_reader)
{
    Map headers = [:]

    // Load the author's language model.
    SectionReader_tidy_hmm lm_file_reader = new SectionReader_tidy_hmm(hmm_reader)

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

    def init_probs = load_section(headers, lm_file_reader, 'init_line_num', 'init', ~/^(\S++)(\s++)(\S++).*/)
    def trans_probs = load_section(headers, lm_file_reader, 'trans_line_num', 'transition', ~/^(\S++)\s++(\S++)\s++(\S++).*/)
    def emiss_probs = load_section(headers, lm_file_reader, 'emiss_line_num', 'emission', ~/^(\S++)\s++(\S++)\s++(\S++).*/)

    [headers:headers, init:init_probs, transition:trans_probs, emission:emiss_probs]
}

def load_hmm_safe(Reader hmm_reader)
{
    try {
        load_hmm(hmm_reader)
    } catch (Exception ex) {
        System.err.println "Exception loading model file : ${ex.message}"
        null
    }
}

// A filter for BufferedReader that will detect when we enter and exit ARPA file sections.
class SectionReader_tidy_hmm extends BufferedReader
{
    boolean inSection
    String currentLine

    SectionReader_tidy_hmm(Reader reader)
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

#!/usr/bin/env groovy

// diff_hmm.groovy hmm_1 hmm_2

def data_1_file = new File(args[0])

def data_2_file = new File(args[1])

System.out.withWriter {
    it.withPrintWriter { printer ->
        diff_mallet_files(data_1_file, data_2_file, printer)
    }
}

void diff_mallet_files(File data_1_file, File data_2_file, def printer)
{
    def data_1_reader = new MALLET_Reader(data_1_file)
    def data_2_reader = new MALLET_Reader(data_2_file)

    def mismatches = 0
    def mismatch_details = 5

    def done = false
    while (!done) {
        def data_1 = data_1_reader.next()
        def data_2 = data_2_reader.next()
        if (data_1 != data_2) {
            mismatches += 1
            if (mismatch_details-- > 0) {
                report_mismatch_details(data_1, data_2, printer)
                printer.println()
            }
        }
        done = (data_1 == null) && (data_2 == null)
    }

    printer.println "${mismatches} mismatched rows"

    if (data_1_reader.all_features != data_2_reader.all_features) {
        report_mismatch_details(data_1_reader.all_features, data_2_reader.all_features, printer)
        printer.println()
    }

    data_1_reader.counts.keySet().each { count_key ->
        if (data_1_reader.counts[count_key] != data_2_reader.counts[count_key])
            printer.print "MISMATCH "
        printer.println "$count_key\t${data_1_reader.counts[count_key]}\t${data_2_reader.counts[count_key]}"

    }

    printer.println " === end report ==="
}

//def data_not_equal(Map data_1, Map data_2) {
//    def keys_1 = data_1.keySet()
//    def keys_2 = data_2.keySet()
//
//    def missing_keys = keys_1 - keys_2
//    if (missing_keys) {
//        printer.println "${missing_keys.size()} missing keys.  ${missing_keys.take(10).join(' ')}"
//    }
//}

def report_mismatch_details(Map data_1, Map data_2, def printer) {
    if (data_1 == null) {
        printer.println "Extra data ${data_2[MALLET_Reader.LABEL]} ${data_2.keySet().size() - 1}"
        return
    }
    if (data_2 == null) {
        printer.println "Missing data ${data_1[MALLET_Reader.LABEL]} ${data_1.keySet().size() - 1}"
        return
    }
    def keys_1 = data_1.keySet()
    def keys_2 = data_2.keySet()

    def missing_keys = keys_1 - keys_2
    if (missing_keys) {
        printer.println "${missing_keys.size()} missing keys.  ${missing_keys.take(10).join(', ')}"
    }

    def extra_keys = keys_2 - keys_1
    if (extra_keys) {
        printer.println "${extra_keys.size()} extra keys.  ${extra_keys.take(10).join(', ')}"
    }

    def common_keys = keys_1.intersect(keys_2)
    def mismatched_key_count = 0
    def mismatched_key_details = 10
    common_keys.each { key ->
        if (data_1[key] != data_2[key]) {
            mismatched_key_count += 1
            if (mismatched_key_details-- > 0) printer.println "$key ${data_1[key]} != ${data_2[key]}"
        }
    }

    if (mismatched_key_count) {
        printer.println "${mismatched_key_count} mismatched values"
    }
}

class MALLET_Reader {
    static String LABEL = " _LABEL_ "
    BufferedReader reader
    Map counts = [lines:0, bad_lines:0, bad_fields:0, bad_numbers:0, blank_lines:0]
    Set<String> ids = []
    Map all_features = [:]//.withDefault { 0 }

    MALLET_Reader(File data_file) { reader = new BufferedReader(new FileReader(data_file)) }

    Map next() {
        Map result = null

        while (result == null) {
            def line = reader.readLine()
            if (line == null) break
            if (line.trim()) {
                def fields = line.split(/\s+/)
                if (fields.length >= 2) {
                    counts.lines += 1
                    result = [:]
                    ids.add(fields[0])
                    fields = (fields as List).drop(2)
                    if (fields.size() % 2 != 0) counts.bad_fields += 1
                    (fields.size() / 2).times {
                        try {
                            def k = fields[it * 2]
                            def v = Math.round(fields[(it * 2) + 1] as Float)
                            result[k] = v
//                            if (v == null || all_features[k] == null) {
//                                println "huh?"
//                            }
                            all_features.put(k, (all_features.get(k) ?: 0) + v)
//                            all_features[k] = all_features[k] + v
//                            if (v == null || all_features[k] == null) {
//                                println "huh?"
//                            }
                        } catch (NumberFormatException ex) {
                            counts.bad_numbers += 1
                        }
                    }
                    all_features += result
                    result[LABEL] = fields[1]
                } else {
                    counts.bad_lines += 1
                }
            } else {
                counts.blank_lines += 1
            }
        }

        result
    }
}

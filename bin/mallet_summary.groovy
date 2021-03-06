#!/usr/bin/env groovy

// diff_hmm.groovy hmm_1 hmm_2

System.out.withWriter {
    it.withPrintWriter { printer ->
        args.eachWithIndex { path, i ->
            printer.println "=== summary of file #$i : $path"
            summarize_mallet_file(new File(path), printer)
        }
    }
}

void summarize_mallet_file(File data_file, def printer)
{
    def data_reader = new mallet_summary_MALLET_Reader(data_file)

    def labels_and_ids = []
    def data_1
    while (data_1 = data_reader.next()) {
        labels_and_ids << [label:data_1[mallet_summary_MALLET_Reader.LABEL]
                , ident:data_1[mallet_summary_MALLET_Reader.IDENT]]
    }

    data_reader.counts.keySet().sort().each { count_key ->
        printer.println "$count_key\t${data_reader.counts[count_key]}"
    }

    printer.println "data_rows\t${labels_and_ids.size()}"

    List labels = labels_and_ids.inject([labels_and_ids.head().label]) { a, v -> a[-1] == v.label ? a : a << v.label }
    printer.println "# Displaying up to 12 class labels in the order they appear.  If a label is shown more than once then its data is not contiguous."
    printer.println "class_labels\t${labels.size()}\t${labels.take(12)}"
    def idents_by_label = labels_and_ids.groupBy { it.label }

    printer.println "=== instance counts per label ==="
    idents_by_label.keySet().sort().each { label ->
        printer.println "$label\t${idents_by_label[label].size()}"
    }

    printer.println "=== identifier sequencing per label ==="
    idents_by_label.keySet().sort().each { label ->
        def idents = idents_by_label[label].ident
        if (idents.size() > 12) {
//            printer.println "# Displaying first 6 and last 6 identifiers"
            idents = idents[0..5] + idents[-6..-1]
            idents = idents.collect { (it =~ /^(?:.*[\\\/])*+(\d*+)\D*+$/)[0][1] }
            printer.println "$label\t${idents[0..5].join('\t')}\t... \t${idents[-6..-1].join('\t')}"
        } else {
            def m = idents[0] =~ /^(?:.*[\\\/])*+(\d*+)\D*+$/
            idents = idents.collect { (it =~ /^(?:.*[\\\/])*(\d*+)\D*+$/)[0][1] }
            printer.println "$label\t${idents.join('\t')}"
        }
    }

    printer.println "=== features ==="
    Map all_features = data_reader.all_features
    printer.println "total_number_of_features\t${all_features.size()}"
    printer.println "sum_of_all_feature_values\t${all_features.values().sum()}"
    printer.println "=== first 25 features ==="
    all_features.keySet().sort().take(25).each { printer.println "$it\t${all_features[it]}"}
    printer.println "=== last 25 features ==="
    all_features.keySet().sort().reverse().take(25).reverse().each { printer.println "$it\t${all_features[it]}"}
    printer.println "=== end report ==="
}

class mallet_summary_MALLET_Reader {
    static String LABEL = " _LABEL_ "
    static String IDENT = " _IDENT_ "
    BufferedReader reader
    Map counts = [lines:0, bad_lines:0, bad_fields:0, bad_numbers:0, blank_lines:0]
    Map all_features = [:]

    mallet_summary_MALLET_Reader(File data_file) { reader = new BufferedReader(new FileReader(data_file)) }

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
                    def ident = fields[0]
                    def label = fields[1]
                    fields = (fields as List).drop(2)
                    if (fields.size() % 2 != 0) counts.bad_fields += 1
                    (fields.size() / 2).times {
                        def k = fields[it * 2]
                        def f = fields[(it * 2) + 1]
                        try {
                            def v = Math.round(f as Float)
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
                            System.err.println "Bad number: '$f' in $line"
                            counts.bad_numbers += 1
                        }
                    }
                    all_features += result
                    result[IDENT] = ident
                    result[LABEL] = label
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

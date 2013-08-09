
def n = args[0] as Integer

println "N = $n"
println()

List<List> triangle(n) {
    def one = 1 as BigInteger

    if (n < 2) {
        [[one]]
    } else {
        def prefix = triangle(n - 1)
        def prev_row = prefix.last()
        prefix << [one] + (0..<(prev_row.size()-1)).collect { prev_row[it] + prev_row[it + 1] } + [one]
    }
}

triangle(n).each { println it }

println()

def v = []

def lines = (0..<n).collect { i ->
    def one = 1 as BigInteger
    if (i > 1) { v = [one] + (0..<(i-1)).collect { v[it] + v[it+1] } }
    v << one
    v.join(' ')
}

def midway = lines[-1].length() / 2

lines.each { print(" " * (midway - (it.length() / 2))) ; println it }

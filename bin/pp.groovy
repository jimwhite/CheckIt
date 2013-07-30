#!/usr/bin/env groovy

args.each {
    new File(it).withReader { reader ->
        if (args.size() > 1) {
            println "=== $it ==="
        }
        System.out.withWriter { writer ->
            def printer = new IndentWriter(writer)
            def sexpr
            while ((sexpr = read_one_sexp(reader)) != null) {
                printer.println()
                printTree(sexpr, printer)
                printer.println()
            }
        }
    }
}

def printTree(Object tree, IndentWriter writer)
{
    if (tree instanceof List) {
        writer.print "("
        def indent = writer + 1
        if (!tree.isEmpty()) {
            def head = tree.head()
            if (head instanceof List) {
                printTree(head, indent)
            } else {
                writer.print sexpString(head.toString())
            }
            def tail = tree.tail()
            tail.each { if (tail.size() > 1) indent.println() ; printTree(it, indent) }
        }
        indent.print ")"
    } else {
        writer.print " " + sexpString(tree.toString())
//        writer.print " '$tree'"
    }
}

def sexpString(String str)
{
    str = str.replace('\\', '\\\\').replace("(", "\\(").replace(")", "\\)").replace("\"", "\\\"")
    (!str || str.contains(" ") || str.contains("\\")) ? "\"" + str + "\"" : str
}

List read_one_sexp(Reader reader)
{
    List<List> stack = []
    List sexprs = []

    Integer cint

    loop:
    while ((cint = reader.read()) >= 0) {
        Character c = cint
        switch (c) {
            case ')':
                sexprs = stack.pop() << sexprs

                // We read only one complete sexp.
                if (stack.size() < 1) break loop
                break

            case '(':
                stack.push(sexprs)
                sexprs = []
                break

            case '"':
                def str = new StringBuilder()
                while ((cint = reader.read()) >= 0) {
                    if (cint == '"') break
                    if (cint == '\\') cint = reader.read()
                    str.append(cint as Character)
                }
                sexprs << str.toString()
                break

            default:
                if (!c.isWhitespace()) {
                    def token = new StringBuilder()
                    token.append(c)
                    reader.mark(1)
                    while ((cint = reader.read()) >= 0) {
                        if ("() \t\n\r".indexOf(cint) >= 0) {
                            // Don't bother unreading this delimiter if it is whitespace since we'ld skip it anyhow.
                            if (!Character.isWhitespace(cint)) reader.reset()
                            break
                        }
                        token.append(cint as Character)
                        reader.mark(1)
                    }
                    sexprs << token.toString()
                }
        }
    }

    assert stack.size() == 0

    sexprs ? sexprs[0] : null
}

class IndentWriter extends PrintWriter
{
    protected boolean needIndent = true;
    protected String indentString;
    protected int indentLevel = 0;

    public IndentWriter(Writer w) { this(w, "  ", 0, true); }
    public IndentWriter(Writer w, String indent, int level, boolean needs)
    { super(w, true); indentString = indent; indentLevel = level; needIndent = needs }

    public int getIndent() { return indentLevel; }

    public IndentWriter plus(int i) {
        return new IndentWriter(out, indentString, indentLevel + i, needIndent);
    }

    public IndentWriter minus(int i) {
        return (plus(-i));
    }

    public IndentWriter next() { return plus(1); }
    public IndentWriter previous() { return minus(1); }

    protected void printIndent() {
        needIndent = false;
        super.print(indentString * indentLevel);
    }

    protected void checkIndent() { if (needIndent) { printIndent(); }; }

    public void println() { super.println(); needIndent = true; }

    public void print(boolean b) { checkIndent(); super.print(b); }
    public void print(char c) { checkIndent(); super.print(c); }
    public void print(char[] s) { checkIndent(); super.print(s); }
    public void print(double d) { checkIndent(); super.print(d); }
    public void print(float f) { checkIndent(); super.print(f); }
    public void print(int i) { checkIndent(); super.print(i); }
    public void print(long l) { checkIndent(); super.print(l); }
    public void print(Object obj) { checkIndent(); super.print(obj); }
    public void print(String s) { checkIndent(); super.print(s); }

// public void close() { }
// public void closeForReal() { super.close() }
}

// A walkthrough of what happens when you compile and run a Java program.
//
// Step 1 — write source:        this file
// Step 2 — compile to bytecode: javac BytecodeWalkthrough.java   produces BytecodeWalkthrough.class
// Step 3 — disassemble:         javap -c BytecodeWalkthrough     shows the JVM instructions
// Step 4 — run:                 java BytecodeWalkthrough
//
// The class file produced in step 2 is the *portable* artifact — it runs on
// any OS that has a JVM. You can ship it to a colleague on Linux and it works,
// even though you compiled on Windows or macOS.

public class BytecodeWalkthrough {
    public static void main(String[] args) {
        int a = 5;
        int b = 7;
        int sum = a + b;
        System.out.println("a + b = " + sum);

        // If you run `javap -c BytecodeWalkthrough` after compiling, you'll see
        // JVM opcodes like iconst_5, istore_1, iadd, getstatic, invokevirtual.
        // Those are the instructions the JVM actually executes — not these
        // Java keywords.
    }
}

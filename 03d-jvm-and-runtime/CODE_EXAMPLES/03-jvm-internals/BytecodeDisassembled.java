public class BytecodeDisassembled {

    // After compiling this file, run:
    //     javap -c BytecodeDisassembled
    // You'll see the bytecode for each method below -- iconst_*, iadd, invokevirtual, etc.
    // This is the actual portable instruction set the JVM executes.

    public static int addThree(int a, int b, int c) {
        return a + b + c;
    }

    public static int squareIfPositive(int n) {
        if (n > 0) return n * n;
        return 0;
    }

    public static String greet(String name) {
        return "hello, " + name;
    }

    public static void main(String[] args) {
        System.out.println(addThree(1, 2, 3));
        System.out.println(squareIfPositive(5));
        System.out.println(squareIfPositive(-1));
        System.out.println(greet("world"));
        System.out.println();
        System.out.println("Now run:  javap -c BytecodeDisassembled");
        System.out.println("to disassemble this class file and see the bytecode for each method.");
    }
}

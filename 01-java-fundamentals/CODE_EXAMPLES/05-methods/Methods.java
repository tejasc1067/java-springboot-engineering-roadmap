// A method takes inputs (parameters), does work, and optionally returns a value.
// Same shape as functions in any other language; Java just makes you put them
// inside a class.

public class Methods {

    public static void main(String[] args) {
        // No-input, no-output method.
        greet();

        // One-input, no-output.
        greet("alice");

        // Two-input, returns int.
        int sum = add(3, 4);
        System.out.println("3 + 4 = " + sum);

        // Returning early on a special case.
        System.out.println("abs(-7) = " + absolute(-7));
        System.out.println("abs( 5) = " + absolute(5));
    }

    static void greet() {
        System.out.println("hi");
    }

    static void greet(String name) {
        System.out.println("hi, " + name);
    }

    static int add(int a, int b) {
        return a + b;
    }

    static int absolute(int n) {
        if (n >= 0) return n;
        return -n;
    }
}

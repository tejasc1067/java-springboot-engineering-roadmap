// Overloading: same method name, different parameter types. The compiler picks
// the right one at the call site based on the argument types.

public class Overloading {

    public static void main(String[] args) {
        System.out.println("add(1, 2)              = " + add(1, 2));
        System.out.println("add(1.5, 2.5)          = " + add(1.5, 2.5));
        System.out.println("add(\"hi\", \" you\")      = " + add("hi", " you"));

        // What about calling with int, double? The compiler promotes the int
        // to double and picks the double version.
        System.out.println("add(1, 2.5)            = " + add(1, 2.5));
    }

    static int    add(int a, int b)       { return a + b; }
    static double add(double a, double b) { return a + b; }
    static String add(String a, String b) { return a + b; }

    // Note: you CAN'T have these two together — return type alone doesn't
    // count toward the signature:
    //   static int    foo(int x) { ... }
    //   static String foo(int x) { ... }      // compile error: duplicate method
}

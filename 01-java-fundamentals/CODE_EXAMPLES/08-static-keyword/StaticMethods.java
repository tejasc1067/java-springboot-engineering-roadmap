// A static method belongs to the class. It's called without creating an
// object. The classic use: stateless utility functions.

public class StaticMethods {
    public static void main(String[] args) {

        // No `new MathUtil()` anywhere — call straight on the class.
        System.out.println("square(7) = " + MathUtil.square(7));
        System.out.println("max(5, 9) = " + MathUtil.max(5, 9));

        // The JDK is full of static utilities you've already used:
        System.out.println("Math.max(1, 2)        = " + Math.max(1, 2));
        System.out.println("Integer.parseInt(\"42\") = " + Integer.parseInt("42"));
        System.out.println("String.join(\"-\", \"a\", \"b\") = " + String.join("-", "a", "b"));

        // A static method CAN'T use `this` (no current object) and CAN'T access
        // non-static fields of its class without an explicit instance.
    }
}

class MathUtil {
    // Pure function: input -> output, no instance state involved.
    static int square(int n) {
        return n * n;
    }

    static int max(int a, int b) {
        return (a > b) ? a : b;
    }
}

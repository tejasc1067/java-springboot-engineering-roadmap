// Arithmetic, comparison, and logical operators, including the corner cases
// that catch beginners out: integer division, the modulo operator, and
// short-circuit evaluation of && and ||.

public class Operators {
    public static void main(String[] args) {

        int a = 10, b = 3;

        System.out.println("Arithmetic:");
        System.out.println("  a + b = " + (a + b));
        System.out.println("  a - b = " + (a - b));
        System.out.println("  a * b = " + (a * b));
        System.out.println("  a / b = " + (a / b) + "   (integer division — fraction discarded)");
        System.out.println("  a % b = " + (a % b) + "   (remainder)");

        // Mix a double and observe the result is a double now.
        System.out.println("  a / b (with double) = " + ((double) a / b));

        System.out.println("\nComparison (produces boolean):");
        System.out.println("  a == b: " + (a == b));
        System.out.println("  a != b: " + (a != b));
        System.out.println("  a >  b: " + (a > b));
        System.out.println("  a <= b: " + (a <= b));

        // Short-circuit: if the left side is false, && skips the right side entirely.
        // This is what makes the `obj != null && obj.something()` idiom safe.
        String name = null;
        boolean isAlice = (name != null && name.equals("alice"));
        System.out.println("\nShort-circuit && with null:");
        System.out.println("  name != null && name.equals(\"alice\") -> " + isAlice + "   (no NPE)");

        // || stops at the first true.
        boolean granted = isAdmin() || hasPermission();   // hasPermission() never runs if isAdmin() is true
        System.out.println("  granted = " + granted);
    }

    static boolean isAdmin() {
        System.out.println("    isAdmin() called");
        return true;
    }

    static boolean hasPermission() {
        System.out.println("    hasPermission() called — you won't see this line");
        return true;
    }
}

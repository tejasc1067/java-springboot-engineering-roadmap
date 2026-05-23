// Strings are immutable — every "modification" returns a new String.
// Forgetting to capture the return value is the most common String bug.

public class StringImmutability {

    public static void main(String[] args) {

        String s = "hello";

        // Bug: we call a method but throw away the result.
        s.toUpperCase();
        System.out.println("after s.toUpperCase() (discarded): " + s);   // still "hello"

        // Fix: assign the result.
        s = s.toUpperCase();
        System.out.println("after s = s.toUpperCase():        " + s);    // "HELLO"

        // The same trap with trim, replace, etc.
        String name = "  alice  ";
        name.trim();
        System.out.println("\nafter name.trim() (discarded): [" + name + "]");

        name = name.trim();
        System.out.println("after name = name.trim():     [" + name + "]");

        // Chaining is fine because each call returns a new String, and we
        // capture only the final one.
        String chained = "  Hello, Java  ".trim().toLowerCase().replace("java", "world");
        System.out.println("\nchained = [" + chained + "]");
    }
}

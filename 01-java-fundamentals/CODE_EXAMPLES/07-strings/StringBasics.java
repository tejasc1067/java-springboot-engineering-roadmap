// String declaration, the most common methods, and basic concatenation.

public class StringBasics {

    public static void main(String[] args) {

        String name = "alice";
        String greeting = "hello, " + name + "!";   // simple concatenation
        System.out.println(greeting);

        // Length — String.length() IS a method (note the parens, unlike array.length)
        System.out.println("length = " + greeting.length());

        // Character at an index
        System.out.println("first char = " + greeting.charAt(0));

        // Substring — start (inclusive) to end (exclusive)
        System.out.println("first 5 chars = [" + greeting.substring(0, 5) + "]");

        // Case
        System.out.println("upper = " + name.toUpperCase());
        System.out.println("lower = " + "HELLO".toLowerCase());

        // Contains, starts/ends with, indexOf
        System.out.println("contains 'lice': " + name.contains("lice"));
        System.out.println("starts with 'al': " + name.startsWith("al"));
        System.out.println("indexOf 'i': " + name.indexOf("i"));

        // Trim whitespace
        String padded = "   hello   ";
        System.out.println("trimmed: [" + padded.trim() + "]");

        // Replace
        System.out.println(greeting.replace("alice", "bob"));

        // Split and join
        String csv = "apple,banana,cherry";
        String[] fruits = csv.split(",");
        System.out.println("split count = " + fruits.length);
        System.out.println("joined back: " + String.join(" | ", fruits));

        // Format
        String formatted = String.format("user=%s, age=%d", "alice", 30);
        System.out.println(formatted);
    }
}

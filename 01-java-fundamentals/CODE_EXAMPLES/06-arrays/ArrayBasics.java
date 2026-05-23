// Arrays: fixed-size sequences. Two ways to create, two ways to iterate,
// one common surprise (println prints garbage — use java.util.Arrays.toString).

public class ArrayBasics {

    public static void main(String[] args) {

        // Literal: known values upfront
        int[] scores = {90, 85, 78, 60, 45};

        // Allocation: fill in later
        String[] names = new String[3];
        names[0] = "alice";
        names[1] = "bob";
        names[2] = "carol";

        // .length is a FIELD (no parens), unlike String.length() which is a METHOD
        System.out.println("scores has " + scores.length + " elements");

        // Indexing is zero-based
        System.out.println("first score = " + scores[0]);
        System.out.println("last  score = " + scores[scores.length - 1]);

        // Classic for — use when you need the index
        System.out.println("\nClassic for:");
        for (int i = 0; i < scores.length; i++) {
            System.out.println("  scores[" + i + "] = " + scores[i]);
        }

        // Enhanced for — use when you don't
        System.out.println("\nEnhanced for:");
        for (String name : names) {
            System.out.println("  " + name);
        }

        // Printing the array directly: useless
        System.out.println("\nprintln(scores) directly: " + scores);

        // The right way to print
        System.out.println("Arrays.toString(scores): " + java.util.Arrays.toString(scores));
    }
}

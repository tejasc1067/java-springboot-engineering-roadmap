// Classic counting for-loop, and the off-by-one bug you'll see in every
// junior dev's code at some point.

public class ForLoop {
    public static void main(String[] args) {

        int[] scores = {90, 85, 78, 60, 45};

        // Correct: i < length, so the last valid index (length - 1) is included
        // and we don't run past the end.
        System.out.println("Correct loop:");
        for (int i = 0; i < scores.length; i++) {
            System.out.println("  scores[" + i + "] = " + scores[i]);
        }

        // The classic off-by-one bug, demonstrated and caught.
        System.out.println("\nOff-by-one bug (i <= length):");
        try {
            for (int i = 0; i <= scores.length; i++) {
                System.out.println("  scores[" + i + "] = " + scores[i]);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("  caught: " + e.getMessage());
        }

        // Counting backwards is common too.
        System.out.println("\nBackwards:");
        for (int i = scores.length - 1; i >= 0; i--) {
            System.out.println("  scores[" + i + "] = " + scores[i]);
        }
    }
}

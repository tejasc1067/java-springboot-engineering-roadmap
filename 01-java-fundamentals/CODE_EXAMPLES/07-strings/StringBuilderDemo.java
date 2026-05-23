// String concatenation in a loop creates a new String object every iteration.
// For small loops the cost is invisible. For large ones, it's measurable —
// O(n^2) total work because each concat copies the accumulated string.
//
// StringBuilder uses a single growable buffer. O(n) total. Always reach for
// it when building up a string inside a loop.

public class StringBuilderDemo {

    public static void main(String[] args) {

        int n = 50_000;

        // Slow: String += in a loop. Each iteration allocates a new String.
        long t1 = System.nanoTime();
        String slow = "";
        for (int i = 0; i < n; i++) {
            slow += "x";
        }
        long t2 = System.nanoTime();

        // Fast: StringBuilder, one buffer that grows as needed.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("x");
        }
        String fast = sb.toString();
        long t3 = System.nanoTime();

        System.out.println("String += loop : " + ((t2 - t1) / 1_000_000) + " ms");
        System.out.println("StringBuilder  : " + ((t3 - t2) / 1_000_000) + " ms");
        System.out.println("same result    : " + slow.equals(fast));

        // For one-off concatenations, you don't need StringBuilder — the
        // compiler optimizes a + b + c into a single StringBuilder behind
        // the scenes. The rule of thumb: reach for StringBuilder INSIDE LOOPS.
    }
}

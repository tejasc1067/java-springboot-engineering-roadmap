// `break`    -> exit the loop entirely
// `continue` -> skip the rest of THIS iteration; jump to the next one

public class BreakAndContinue {
    public static void main(String[] args) {

        System.out.println("continue — skip multiples of 3:");
        for (int i = 1; i <= 10; i++) {
            if (i % 3 == 0) continue;
            System.out.println("  " + i);
        }

        System.out.println("\nbreak — stop at the first match:");
        int[] data = {5, 8, 13, 21, 34};
        int firstOver10 = -1;
        for (int n : data) {
            if (n > 10) {
                firstOver10 = n;
                break;
            }
        }
        System.out.println("  first value > 10 is " + firstOver10);

        // In nested loops, break only exits the inner loop by default.
        // Labels (rarely used, but they exist) let you break the outer one.
        System.out.println("\nLabeled break — exit both loops at once:");
        outer:
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                if (i * j > 6) {
                    System.out.println("  breaking at i=" + i + ", j=" + j);
                    break outer;
                }
            }
        }
    }
}

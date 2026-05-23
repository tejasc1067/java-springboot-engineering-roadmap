// `while` is for "keep going until some condition flips."
// The classic bug: forgetting to update the variable the condition depends on,
// resulting in an infinite loop. We use a safety counter here so this program
// terminates if you accidentally break the update.

public class WhileLoop {
    public static void main(String[] args) {

        // Halve n until it's <= 1. Demonstrates a use case where you don't
        // know how many iterations in advance.
        int n = 100;
        while (n > 1) {
            System.out.println("n = " + n);
            n = n / 2;
        }
        System.out.println("final n = " + n);

        // The infinite-loop trap: forget the update.
        // Without the safety counter this would never terminate.
        System.out.println("\nSimulated infinite-loop bug, with a safety brake:");
        int i = 0;
        int safety = 0;
        while (i < 5) {
            System.out.println("  i = " + i);
            // forgot i++ on purpose
            safety++;
            if (safety > 10) {
                System.out.println("  safety brake hit — would have looped forever");
                break;
            }
        }
    }
}

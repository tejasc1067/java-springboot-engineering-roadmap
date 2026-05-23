// `finally` runs whether the try block succeeded, threw, or returned early.
// Used for cleanup that must happen no matter what.
//
// (For closing AutoCloseable resources, prefer try-with-resources — topic 15.)

public class FinallyAlwaysRuns {

    public static void main(String[] args) {

        System.out.println("case 1: normal completion");
        runCase(false);

        System.out.println("\ncase 2: caught exception");
        runCase(true);
    }

    static void runCase(boolean shouldThrow) {
        try {
            System.out.println("  try: doing work");
            if (shouldThrow) {
                throw new RuntimeException("oops");
            }
            System.out.println("  try: finished normally");
        } catch (RuntimeException e) {
            System.out.println("  catch: handled " + e.getMessage());
        } finally {
            System.out.println("  finally: cleanup");
        }
    }
}

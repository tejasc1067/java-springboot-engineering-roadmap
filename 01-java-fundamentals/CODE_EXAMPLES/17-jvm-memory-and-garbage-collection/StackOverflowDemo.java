// Unbounded recursion blows the stack — StackOverflowError. Note this is
// NOT OutOfMemoryError. Stack and heap have separate limits and separate
// failure modes.

public class StackOverflowDemo {

    public static void main(String[] args) {
        try {
            recurse(0);
        } catch (StackOverflowError soe) {
            System.out.println("StackOverflowError caught.");
            System.out.println("Stack depth reached before crash: " + depth);
        }
    }

    static int depth = 0;
    static void recurse(int n) {
        depth = n;
        recurse(n + 1);    // no base case — runs until the stack runs out
    }
}

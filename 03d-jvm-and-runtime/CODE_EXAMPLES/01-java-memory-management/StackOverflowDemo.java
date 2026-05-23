public class StackOverflowDemo {

    static int maxDepthSeen = 0;

    public static void main(String[] args) {
        try {
            recurse(0);
        } catch (StackOverflowError e) {
            System.out.println("StackOverflowError caught at depth ~" + maxDepthSeen);
            System.out.println("(default stack ~512KB-1MB; depth depends on frame size and -Xss)");
        }
    }

    static void recurse(int n) {
        maxDepthSeen = n;
        recurse(n + 1);
    }
}

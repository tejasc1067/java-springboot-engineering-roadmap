public class FinallySwallowsOriginal {

    static String buggy() {
        try {
            throw new RuntimeException("the real bug");
        } finally {
            return "swallowed";
        }
    }

    static void worseBuggy() {
        try {
            throw new RuntimeException("the real bug");
        } finally {
            throw new RuntimeException("replacement from finally");
        }
    }

    public static void main(String[] args) {
        System.out.println("buggy() returned: " + buggy());
        System.out.println("  (the real bug was never seen -- return from finally killed it)");
        System.out.println();

        try {
            worseBuggy();
        } catch (RuntimeException e) {
            System.out.println("worseBuggy() threw: " + e.getMessage());
            System.out.println("  (the real bug was replaced -- never reached the caller)");
        }
    }
}

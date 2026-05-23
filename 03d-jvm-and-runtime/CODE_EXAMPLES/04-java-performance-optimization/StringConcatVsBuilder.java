public class StringConcatVsBuilder {

    public static void main(String[] args) {
        int n = 20_000;

        // 1) + concatenation in a loop -- O(n^2) work because each iteration
        //    allocates a new String of growing size.
        long t1 = System.nanoTime();
        String s = "";
        for (int i = 0; i < n; i++) s = s + "x";
        long concatMs = (System.nanoTime() - t1) / 1_000_000;

        // 2) StringBuilder, with a sensible initial capacity to avoid resize churn.
        long t2 = System.nanoTime();
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append('x');
        String s2 = sb.toString();
        long builderMs = (System.nanoTime() - t2) / 1_000_000;

        System.out.println("+ concat:        " + concatMs + " ms  (length " + s.length() + ")");
        System.out.println("StringBuilder:   " + builderMs + " ms  (length " + s2.length() + ")");
        System.out.println();
        System.out.println("+ concat in a loop is O(n^2) -- each iteration copies the whole string so far.");
    }
}

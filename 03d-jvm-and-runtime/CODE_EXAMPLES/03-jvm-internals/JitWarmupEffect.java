public class JitWarmupEffect {

    public static void main(String[] args) {
        // Run many short trials. The first few include interpreter + compilation cost;
        // later trials run the fully-optimized C2-compiled code.
        int trials = 8;
        long[] times = new long[trials];
        long sink = 0;

        for (int t = 0; t < trials; t++) {
            long start = System.nanoTime();
            for (int i = 0; i < 300_000; i++) {
                sink += hotMethod(i);
            }
            times[t] = (System.nanoTime() - start) / 1_000;   // microseconds
        }

        if (sink == Long.MIN_VALUE) System.out.println("");   // prevent dead-code elim

        System.out.println("trial timings (microseconds):");
        for (int t = 0; t < trials; t++) {
            System.out.println("  trial " + t + ": " + times[t] + " us");
        }
        System.out.println();
        long first = times[0];
        long last = times[trials - 1];
        System.out.println("first trial:  " + first + " us  (cold: interpreter + JIT compile cost)");
        System.out.println("last trial:   " + last  + " us  (warm: running native code)");
        System.out.println("speedup:      " + ((double) first / Math.max(1, last)) + "x");
        System.out.println();
        System.out.println("Run with -XX:+PrintCompilation to see exactly when methods get compiled.");
    }

    static int hotMethod(int n) {
        int x = n;
        for (int i = 0; i < 20; i++) x = x * 31 + i;
        return x;
    }
}

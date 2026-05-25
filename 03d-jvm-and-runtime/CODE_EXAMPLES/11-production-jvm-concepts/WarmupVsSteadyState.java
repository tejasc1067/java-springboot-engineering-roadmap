public class WarmupVsSteadyState {

    public static void main(String[] args) {
        // Cold: fresh JVM has not JIT'd `work` yet.
        long cold = measure();

        // Warm up the same method.
        for (int i = 0; i < 50_000; i++) work(i);

        // Now measure the warm version.
        long warm = measure();

        System.out.println("first measurement (cold, JIT compiling):   " + cold + " us");
        System.out.println("after warmup (C2-compiled, steady-state):  " + warm + " us");
        System.out.println("speedup:                                   ~" + (cold / Math.max(1, warm)) + "x");
        System.out.println();
        System.out.println("In production this gap shows up as a latency spike for the first ~30s");
        System.out.println("after each deploy. Mitigations: warmup routines, readiness probes, AOT/native-image.");
    }

    static long measure() {
        long sink = 0;
        long start = System.nanoTime();
        for (int i = 0; i < 300_000; i++) sink += work(i);
        if (sink == Long.MIN_VALUE) System.out.println("");
        return (System.nanoTime() - start) / 1_000;
    }

    static int work(int n) {
        int x = n;
        for (int i = 0; i < 20; i++) x = x * 31 + i;
        return x;
    }
}

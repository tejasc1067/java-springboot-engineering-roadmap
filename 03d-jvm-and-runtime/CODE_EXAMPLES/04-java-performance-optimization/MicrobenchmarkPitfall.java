public class MicrobenchmarkPitfall {

    public static void main(String[] args) {
        // The two methods do the same work. A naive nanoTime "benchmark"
        // gives wildly different numbers depending on order and JIT state.

        long naiveA = measure(MicrobenchmarkPitfall::workA);
        long naiveB = measure(MicrobenchmarkPitfall::workB);
        System.out.println("NAIVE (no warmup):");
        System.out.println("  workA: " + naiveA + " us");
        System.out.println("  workB: " + naiveB + " us");

        // Warm up both, then measure.
        for (int i = 0; i < 10; i++) { workA(); workB(); }

        long warmedA = measure(MicrobenchmarkPitfall::workA);
        long warmedB = measure(MicrobenchmarkPitfall::workB);
        System.out.println();
        System.out.println("AFTER WARMUP:");
        System.out.println("  workA: " + warmedA + " us");
        System.out.println("  workB: " + warmedB + " us");

        System.out.println();
        System.out.println("Conclusion: the 'cold' numbers measure JIT compilation, not your code.");
        System.out.println("For real microbenchmarks use JMH (org.openjdk.jmh).");
    }

    static long measure(Runnable r) {
        long t = System.nanoTime();
        r.run();
        return (System.nanoTime() - t) / 1_000;
    }

    static long sink;

    static void workA() {
        long s = 0;
        for (int i = 0; i < 1_000_000; i++) s += i * 31;
        sink = s;
    }

    static void workB() {
        long s = 0;
        for (int i = 0; i < 1_000_000; i++) s += i * 31;
        sink = s;
    }
}

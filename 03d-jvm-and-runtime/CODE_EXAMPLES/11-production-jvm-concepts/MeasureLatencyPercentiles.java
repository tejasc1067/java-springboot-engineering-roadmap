import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class MeasureLatencyPercentiles {

    public static void main(String[] args) {
        int n = 100_000;
        long[] samples = new long[n];

        // Simulate request timings: most around 5ms, occasional slow ones, very rare very-slow ones.
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            int roll = rng.nextInt(10_000);
            if (roll < 9_800)       samples[i] = 4_000 + rng.nextInt(2_000);   // 4-6ms, 98%
            else if (roll < 9_990)  samples[i] = 30_000 + rng.nextInt(20_000); // 30-50ms, ~1.9%
            else                    samples[i] = 200_000 + rng.nextInt(50_000);// 200-250ms, ~0.1%
        }

        long avg = 0;
        for (long s : samples) avg += s;
        avg /= n;

        Arrays.sort(samples);
        System.out.println("average: " + (avg / 1000.0) + " ms");
        System.out.println();
        System.out.println("percentile  latency(ms)");
        for (double p : new double[] { 0.50, 0.90, 0.95, 0.99, 0.999 }) {
            long v = samples[(int) (n * p)];
            System.out.printf("  p%-6.2f  %.2f%n", p * 100, v / 1000.0);
        }
        System.out.println();
        System.out.println("Notice: average (around p~84) is much lower than p99.9.");
        System.out.println("Tail percentiles describe the slow customers; the average hides them.");
    }
}

import java.util.HashMap;
import java.util.Map;

public class CachingHotLookup {

    static int slowCalls = 0;

    public static void main(String[] args) {
        int n = 200_000;

        // 1) Uncached: every lookup pays the full cost.
        slowCalls = 0;
        long t1 = System.nanoTime();
        long sumUncached = 0;
        for (int i = 0; i < n; i++) {
            sumUncached += slowLookup(i % 50);    // only 50 distinct inputs, but no memoization
        }
        long uncachedMs = (System.nanoTime() - t1) / 1_000_000;
        int uncachedCalls = slowCalls;

        // 2) Cached: memoize the result.
        slowCalls = 0;
        Map<Integer, Long> cache = new HashMap<>();
        long t2 = System.nanoTime();
        long sumCached = 0;
        for (int i = 0; i < n; i++) {
            int key = i % 50;
            sumCached += cache.computeIfAbsent(key, CachingHotLookup::slowLookup);
        }
        long cachedMs = (System.nanoTime() - t2) / 1_000_000;
        int cachedCalls = slowCalls;

        System.out.println("uncached: " + uncachedMs + " ms, " + uncachedCalls + " slow-call invocations");
        System.out.println("cached:   " + cachedMs   + " ms, " + cachedCalls   + " slow-call invocations");
        System.out.println("sums equal? " + (sumUncached == sumCached));
        System.out.println();
        System.out.println("Caching pays off when input cardinality is much smaller than call count.");
    }

    static long slowLookup(int key) {
        slowCalls++;
        long s = 0;
        for (int i = 0; i < 10_000; i++) s += i * (long) key;
        return s;
    }
}

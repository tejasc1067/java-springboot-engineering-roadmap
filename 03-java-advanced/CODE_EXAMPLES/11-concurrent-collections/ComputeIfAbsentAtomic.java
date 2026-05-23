import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ComputeIfAbsentAtomic {

    static final AtomicInteger compileCount = new AtomicInteger();

    static String expensiveCompute(String key) {
        compileCount.incrementAndGet();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        return key.toUpperCase();
    }

    public static void main(String[] args) throws InterruptedException {
        Map<String, String> cache = new ConcurrentHashMap<>();
        int threadCount = 8;

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                String result = cache.computeIfAbsent("shared-key", ComputeIfAbsentAtomic::expensiveCompute);
                if (!"SHARED-KEY".equals(result)) {
                    System.out.println("unexpected: " + result);
                }
            });
        }

        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        System.out.println("threads ran:                " + threadCount);
        System.out.println("expensiveCompute invocations: " + compileCount.get());
        System.out.println();
        System.out.println("computeIfAbsent runs the function at most once per absent key,");
        System.out.println("even when many threads race for the same key.");
    }
}

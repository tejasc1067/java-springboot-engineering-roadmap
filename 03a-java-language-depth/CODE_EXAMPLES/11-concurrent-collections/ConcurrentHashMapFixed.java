import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashMapFixed {

    public static void main(String[] args) throws InterruptedException {
        Map<Integer, Integer> map = new ConcurrentHashMap<>();
        int threadCount = 8;
        int perThread = 10_000;

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int base = t * perThread;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    map.put(base + i, i);
                }
            });
        }

        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        int expected = threadCount * perThread;
        System.out.println("expected size: " + expected);
        System.out.println("actual size:   " + map.size());
        System.out.println();
        System.out.println("ConcurrentHashMap uses lock striping -- concurrent writes don't corrupt it.");
    }
}

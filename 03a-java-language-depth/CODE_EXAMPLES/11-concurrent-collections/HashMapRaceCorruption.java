import java.util.HashMap;
import java.util.Map;

public class HashMapRaceCorruption {

    public static void main(String[] args) throws InterruptedException {
        Map<Integer, Integer> map = new HashMap<>();
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
        if (map.size() != expected) {
            System.out.println("entries lost or corrupted -- race during put");
        }
        System.out.println();
        System.out.println("plain HashMap is unsafe for concurrent writes.");
        System.out.println("see ConcurrentHashMapFixed.java for the fix.");
    }
}

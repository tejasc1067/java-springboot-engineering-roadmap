import java.util.Vector;

public class VectorIsLegacy {

    public static void main(String[] args) throws InterruptedException {
        Vector<Integer> v = new Vector<>();

        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 100; i++) {
                    if (!v.contains(i)) {
                        v.add(i);
                    }
                }
            });
        }
        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        System.out.println("Vector size after 8 threads doing contains-then-add: " + v.size());
        System.out.println("expected at most 100 (unique values), often higher because");
        System.out.println("contains() and add() are individually atomic but NOT atomic together.");
        System.out.println();
        System.out.println("Vector's synchronized methods only protect each call — not your compound logic.");
        System.out.println("For real concurrency, use ConcurrentHashMap or explicit locks (topic 11).");
    }
}

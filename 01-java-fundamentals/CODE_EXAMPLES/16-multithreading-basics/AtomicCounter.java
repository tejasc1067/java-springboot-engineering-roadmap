// For a single-variable counter, AtomicInteger is simpler and faster than
// synchronized. It's lock-free — uses CPU compare-and-swap instructions
// under the hood.
//
// For more complex atomic operations across multiple fields, you still
// need synchronized (or a higher-level concurrency primitive).

import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounter {

    static AtomicInteger count = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {

        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                count.incrementAndGet();
            }
        };

        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("expected: 200000");
        System.out.println("actual:   " + count.get());
    }
}

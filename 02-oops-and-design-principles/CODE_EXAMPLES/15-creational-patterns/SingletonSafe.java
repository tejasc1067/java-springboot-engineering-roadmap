import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

// The same singleton, made thread-safe by letting the JVM do the locking.
// An enum's values are created exactly once, when the enum class is first used,
// and the JVM guarantees class initialization happens on one thread only.
//
// COMPARE WITH: SingletonBroken.java

enum ConfigLoader {
    INSTANCE;

    ConfigLoader() {
        // An enum constructor can't use the enum's own static fields, so the count lives in Counter.
        Counter.created.incrementAndGet();
        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    String get(String key) { return "value-of-" + key; }
}

class Counter {
    static final AtomicInteger created = new AtomicInteger();
}

public class SingletonSafe {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch startTogether = new CountDownLatch(1);
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try { startTogether.await(); } catch (InterruptedException e) { return; }
                ConfigLoader.INSTANCE.get("db.url");
            });
            threads[i].start();
        }
        startTogether.countDown();
        for (Thread t : threads) t.join();

        int created = Counter.created.get();
        System.out.println("Threads that used ConfigLoader.INSTANCE: 10");
        System.out.println("ConfigLoader objects created:            " + created);
        if (created != 1) throw new AssertionError("expected exactly one instance, got " + created);
        System.out.println("Exactly one, every run.");
    }
}

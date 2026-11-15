import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

// The textbook lazy singleton. Correct with one thread; broken with two.
//
// COMPARE WITH: SingletonSafe.java

class ConfigLoader {
    static final AtomicInteger instancesCreated = new AtomicInteger();
    private static ConfigLoader instance;

    private ConfigLoader() {
        instancesCreated.incrementAndGet();
        pause(50); // stands in for reading a config file - slow enough for other threads to arrive
    }

    static ConfigLoader getInstance() {
        if (instance == null) {          // check...
            instance = new ConfigLoader(); // ...then act. Two threads can both pass the check.
        }
        return instance;
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

public class SingletonBroken {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch startTogether = new CountDownLatch(1);
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try { startTogether.await(); } catch (InterruptedException e) { return; }
                ConfigLoader.getInstance();
            });
            threads[i].start();
        }
        startTogether.countDown(); // release all 10 threads at once
        for (Thread t : threads) t.join();

        int created = ConfigLoader.instancesCreated.get();
        System.out.println("Threads that called getInstance(): 10");
        System.out.println("ConfigLoader objects created:      " + created);
        System.out.println(created > 1
                ? "The \"single\" instance was created " + created + " times."
                : "Got lucky this run - try again; the race is still there.");
    }
}

public class PrivateLockObject {

    // BAD: locks `this`. Any external code that holds the reference can grab the lock.
    static class ExposedThis {
        private int n;
        public synchronized void increment() { n++; }
        public synchronized int get() { return n; }
    }

    // GOOD: lock object is private; nobody outside this class can reach it.
    static class PrivateLock {
        private final Object lock = new Object();
        private int n;
        public void increment() { synchronized (lock) { n++; } }
        public int get() { synchronized (lock) { return n; } }
    }

    public static void main(String[] args) throws InterruptedException {
        ExposedThis exposed = new ExposedThis();

        // A "hostile" (or just careless) outside caller grabs the lock and holds it.
        Thread hog = new Thread(() -> {
            synchronized (exposed) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }, "lock-hog");
        hog.start();
        Thread.sleep(50);

        long t0 = System.nanoTime();
        exposed.increment();   // blocks until the hog releases `this`
        long blockedMs = (System.nanoTime() - t0) / 1_000_000;
        hog.join();

        System.out.println("ExposedThis.increment() was blocked " + blockedMs + " ms by outside code holding `this`.");
        System.out.println();
        System.out.println("PrivateLock has no such exposure -- the lock object is unreachable from outside.");

        PrivateLock safe = new PrivateLock();
        safe.increment();
        System.out.println("PrivateLock.get() = " + safe.get());
    }
}

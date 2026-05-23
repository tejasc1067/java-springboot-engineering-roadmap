public class DeadlockFixedOrdering {

    static final Object lockA = new Object();
    static final Object lockB = new Object();

    // Both threads acquire in the SAME order: A then B. No cycle, no deadlock.
    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                System.out.println("t1 got A, asking for B...");
                sleep(100);
                synchronized (lockB) {
                    System.out.println("t1 got B");
                }
            }
        }, "t1");

        Thread t2 = new Thread(() -> {
            synchronized (lockA) {
                System.out.println("t2 got A, asking for B...");
                sleep(100);
                synchronized (lockB) {
                    System.out.println("t2 got B");
                }
            }
        }, "t2");

        t1.start(); t2.start();
        t1.join(); t2.join();
        System.out.println("both threads finished cleanly.");
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

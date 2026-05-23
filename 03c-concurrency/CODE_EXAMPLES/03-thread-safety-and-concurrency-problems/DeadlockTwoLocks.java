public class DeadlockTwoLocks {

    static final Object lockA = new Object();
    static final Object lockB = new Object();

    public static void main(String[] args) throws InterruptedException {
        // Thread 1: A then B
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                System.out.println("t1 got A, asking for B...");
                sleep(100);
                synchronized (lockB) {
                    System.out.println("t1 got B (will not reach this)");
                }
            }
        }, "t1");

        // Thread 2: B then A -- opposite order
        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                System.out.println("t2 got B, asking for A...");
                sleep(100);
                synchronized (lockA) {
                    System.out.println("t2 got A (will not reach this)");
                }
            }
        }, "t2");

        t1.start(); t2.start();

        // Give them 2 seconds. If still alive, we have a deadlock.
        t1.join(2000); t2.join(2000);
        if (t1.isAlive() && t2.isAlive()) {
            System.out.println();
            System.out.println("DEADLOCK detected. Both threads stuck.");
            System.out.println("see DeadlockFixedOrdering.java for the fix.");
            System.exit(0);
        }
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class DetectDeadlock {

    static final Object lockA = new Object();
    static final Object lockB = new Object();

    public static void main(String[] args) throws InterruptedException {
        // Two threads acquire locks in opposite orders. Classic AB-BA deadlock.
        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                sleep(100);
                synchronized (lockB) {
                    System.out.println("t1 got both -- will not be reached");
                }
            }
        }, "t1");

        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                sleep(100);
                synchronized (lockA) {
                    System.out.println("t2 got both -- will not be reached");
                }
            }
        }, "t2");

        t1.start(); t2.start();
        Thread.sleep(500);     // give them time to deadlock

        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        long[] deadlocked = tmx.findDeadlockedThreads();
        if (deadlocked == null) {
            System.out.println("no deadlock detected (unexpected for this demo).");
        } else {
            System.out.println("deadlock detected!");
            for (ThreadInfo info : tmx.getThreadInfo(deadlocked, true, true)) {
                System.out.println("  thread '" + info.getThreadName() + "' is waiting on " + info.getLockName());
                System.out.println("    held by: " + info.getLockOwnerName());
            }
        }
        System.exit(0);   // hard-exit so the deadlocked threads don't keep JVM alive
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class ThreadDumpProgrammatic {

    public static void main(String[] args) throws InterruptedException {
        // Start a couple of worker threads doing different things so the dump has content.
        Thread sleeper = new Thread(() -> {
            try { Thread.sleep(60_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "long-sleeper");
        sleeper.setDaemon(true);
        sleeper.start();

        final Object lock = new Object();
        Thread holder = new Thread(() -> {
            synchronized (lock) {
                try { Thread.sleep(60_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }, "lock-holder");
        holder.setDaemon(true);
        holder.start();
        Thread.sleep(100);   // let holder grab the lock

        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                System.out.println("(unreachable in the demo window)");
            }
        }, "lock-waiter");
        waiter.setDaemon(true);
        waiter.start();
        Thread.sleep(100);

        // Dump.
        ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = tmx.dumpAllThreads(true, true);
        System.out.println("=== thread dump (" + infos.length + " threads) ===");
        for (ThreadInfo info : infos) {
            if (info.getThreadName().startsWith("long-sleeper")
                || info.getThreadName().startsWith("lock-")) {
                System.out.println();
                System.out.println("\"" + info.getThreadName() + "\" state=" + info.getThreadState());
                if (info.getLockName() != null) {
                    System.out.println("  waiting on: " + info.getLockName());
                    if (info.getLockOwnerName() != null) {
                        System.out.println("  held by:    " + info.getLockOwnerName());
                    }
                }
                StackTraceElement[] stack = info.getStackTrace();
                for (int i = 0; i < Math.min(stack.length, 3); i++) {
                    System.out.println("    at " + stack[i]);
                }
            }
        }
    }
}

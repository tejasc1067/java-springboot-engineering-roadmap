import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadLocalLeakInPool {

    static final ThreadLocal<String> userCtx = new ThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();   // one worker, reused

        pool.execute(() -> {
            userCtx.set("alice");
            System.out.println("task A on " + Thread.currentThread().getName() + ": user=" + userCtx.get());
            // forgot to userCtx.remove() -- value leaks onto the worker thread
        });

        pool.execute(() -> {
            // The next task lands on the SAME worker. It never set the value,
            // but it still sees task A's user.
            System.out.println("task B on " + Thread.currentThread().getName() + ": user=" + userCtx.get() + "  <-- LEAKED!");
        });

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
        System.out.println();
        System.out.println("see ThreadLocalProperCleanup.java for the fix.");
    }
}

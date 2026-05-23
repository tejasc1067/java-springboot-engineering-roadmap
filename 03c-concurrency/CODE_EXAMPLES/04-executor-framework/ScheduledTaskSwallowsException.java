import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScheduledTaskSwallowsException {

    public static void main(String[] args) throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        AtomicInteger naked = new AtomicInteger();
        AtomicInteger wrapped = new AtomicInteger();

        // BAD: throws on the 3rd run. Scheduler silently cancels the task. No more runs.
        scheduler.scheduleAtFixedRate(() -> {
            int n = naked.incrementAndGet();
            System.out.println("[naked]   run " + n);
            if (n == 3) throw new RuntimeException("boom");
        }, 0, 100, TimeUnit.MILLISECONDS);

        // GOOD: same task, but catch Throwable so the scheduler never sees the exception.
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int n = wrapped.incrementAndGet();
                System.out.println("[wrapped] run " + n);
                if (n == 3) throw new RuntimeException("boom");
            } catch (Throwable t) {
                System.out.println("[wrapped] caught: " + t.getMessage() + " -- next tick will still fire");
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        Thread.sleep(800);
        scheduler.shutdownNow();
        scheduler.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("naked   ran " + naked.get() + " times (stopped at first throw)");
        System.out.println("wrapped ran " + wrapped.get() + " times");
    }
}

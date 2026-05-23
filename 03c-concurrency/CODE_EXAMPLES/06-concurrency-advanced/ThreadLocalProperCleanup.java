import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadLocalProperCleanup {

    static final ThreadLocal<String> userCtx = new ThreadLocal<>();

    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        pool.execute(() -> withUser("alice", () ->
            System.out.println("task A: user=" + userCtx.get())));

        pool.execute(() -> {
            // No user set this time. The previous one was cleaned up, so we see null.
            System.out.println("task B: user=" + userCtx.get() + "  <-- clean");
        });

        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
    }

    static void withUser(String user, Runnable body) {
        userCtx.set(user);
        try { body.run(); }
        finally { userCtx.remove(); }     // <-- the line that matters
    }
}

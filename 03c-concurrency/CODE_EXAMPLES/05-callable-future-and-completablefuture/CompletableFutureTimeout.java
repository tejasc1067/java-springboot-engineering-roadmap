import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class CompletableFutureTimeout {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // orTimeout: complete EXCEPTIONALLY with TimeoutException if not done in time.
        CompletableFuture<String> withFail = slow("would-be-result", 500)
            .orTimeout(200, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> "timed out: " + ex.getClass().getSimpleName());
        System.out.println("orTimeout result: " + withFail.get());

        // completeOnTimeout: complete SUCCESSFULLY with a default value.
        CompletableFuture<String> withDefault = slow("would-be-result", 500)
            .completeOnTimeout("fallback", 200, TimeUnit.MILLISECONDS);
        System.out.println("completeOnTimeout result: " + withDefault.get());

        // Fast-enough call still wins.
        CompletableFuture<String> fast = slow("on-time", 50)
            .orTimeout(200, TimeUnit.MILLISECONDS);
        System.out.println("fast call result: " + fast.get());
    }

    static CompletableFuture<String> slow(String value, long ms) {
        return CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return value;
        });
    }
}

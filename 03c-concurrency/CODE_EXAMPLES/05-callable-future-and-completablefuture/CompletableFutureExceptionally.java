import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CompletableFutureExceptionally {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // exceptionally: recover with a fallback value, then continue.
        CompletableFuture<String> recovered = CompletableFuture
            .<String>supplyAsync(() -> { throw new RuntimeException("network down"); })
            .exceptionally(ex -> "fallback")
            .thenApply(String::toUpperCase);
        System.out.println("recovered: " + recovered.get());

        // handle: see both success and failure in one place.
        CompletableFuture<String> handled = CompletableFuture
            .<String>supplyAsync(() -> { throw new RuntimeException("boom"); })
            .handle((value, ex) -> {
                if (ex != null) return "handled: " + ex.getCause().getMessage();
                return "ok: " + value;
            });
        System.out.println(handled.get());

        // Without exceptionally/handle, get() throws ExecutionException wrapping the cause.
        CompletableFuture<String> raw = CompletableFuture
            .supplyAsync(() -> { throw new RuntimeException("uncaught"); });
        try {
            raw.get();
        } catch (ExecutionException e) {
            System.out.println("raw .get() threw: " + e.getCause().getMessage());
        }
    }
}

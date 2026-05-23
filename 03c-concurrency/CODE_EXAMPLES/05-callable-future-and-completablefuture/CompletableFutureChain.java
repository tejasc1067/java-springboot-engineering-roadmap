import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CompletableFutureChain {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // thenApply: sync transform.
        CompletableFuture<Integer> chained = CompletableFuture
            .supplyAsync(() -> "hello world")
            .thenApply(String::length)            // "hello world" -> 11
            .thenApply(n -> n * 2);               // 11 -> 22
        System.out.println("thenApply chain: " + chained.get());

        // thenCompose: each step itself returns a CompletableFuture, and the chain flattens.
        CompletableFuture<String> composed = lookupUserId("alice")
            .thenCompose(CompletableFutureChain::lookupProfile);
        System.out.println("thenCompose result: " + composed.get());

        // What thenApply would give us if we used it instead -- a nested future, awkward to unwrap.
        CompletableFuture<CompletableFuture<String>> nested = lookupUserId("alice")
            .thenApply(CompletableFutureChain::lookupProfile);
        System.out.println("thenApply would give: " + nested.get().getClass().getSimpleName());
    }

    static CompletableFuture<Integer> lookupUserId(String name) {
        return CompletableFuture.supplyAsync(() -> name.hashCode() & 0xFFFF);
    }

    static CompletableFuture<String> lookupProfile(int userId) {
        return CompletableFuture.supplyAsync(() -> "profile-of-" + userId);
    }
}

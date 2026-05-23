import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CompletableFutureCombine {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        long start = System.currentTimeMillis();

        CompletableFuture<String>  nameF = CompletableFuture.supplyAsync(() -> slow("Alice", 200));
        CompletableFuture<Integer> ageF  = CompletableFuture.supplyAsync(() -> slow(30, 200));

        // thenCombine fires when BOTH source futures are done. They ran in parallel.
        CompletableFuture<String> combined = nameF.thenCombine(ageF, (n, a) -> n + " is " + a);
        String result = combined.get();

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("result: " + result);
        System.out.println("elapsed: " + elapsed + "ms (two 200ms calls in parallel)");
    }

    static <T> T slow(T value, long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return value;
    }
}

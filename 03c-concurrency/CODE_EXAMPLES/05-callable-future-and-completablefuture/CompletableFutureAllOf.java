import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class CompletableFutureAllOf {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        long start = System.currentTimeMillis();

        List<CompletableFuture<String>> futures = List.of(1, 2, 3, 4, 5).stream()
            .map(id -> CompletableFuture.supplyAsync(() -> fetchItem(id)))
            .collect(Collectors.toList());

        // allOf gives us a Void future that completes when every input completes.
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        all.join();   // block here only

        // Now harvest the results from each individual future.
        List<String> results = futures.stream().map(CompletableFuture::join).collect(Collectors.toList());

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("results: " + results);
        System.out.println("elapsed: " + elapsed + "ms (5 x 150ms calls in parallel)");
    }

    static String fetchItem(int id) {
        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return "item-" + id;
    }
}

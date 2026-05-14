import java.util.concurrent.CompletableFuture;

public class CompletableFutureChainingExample {

    public static void main(String[] args) {

        CompletableFuture<String> future =
                CompletableFuture.supplyAsync(() -> {

                    return "User Data";

                }).thenApply(data -> {

                    return data + " Processed";

                }).thenApply(result -> {

                    return result + " Successfully";
                });

        System.out.println(
                future.join()
        );
    }
}
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SynchronizedListIterationLocked {

    public static void main(String[] args) throws InterruptedException {
        List<Integer> shared = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 10_000; i++) shared.add(i);

        Thread mutator = new Thread(() -> {
            for (int i = 0; i < 10_000; i++) {
                shared.add(99_999);
            }
        });

        Thread reader = new Thread(() -> {
            int sum = 0;
            synchronized (shared) {
                for (int v : shared) {
                    sum += v;
                }
            }
            System.out.println("reader finished safely, sum=" + sum);
        });

        mutator.start();
        reader.start();
        mutator.join();
        reader.join();

        System.out.println();
        System.out.println("Manual `synchronized (shared)` blocks the mutator during iteration.");
        System.out.println("For finer-grained concurrency, CopyOnWriteArrayList avoids the wait entirely.");
    }
}

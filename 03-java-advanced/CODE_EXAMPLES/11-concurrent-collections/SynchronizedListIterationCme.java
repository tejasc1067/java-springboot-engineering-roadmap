import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SynchronizedListIterationCme {

    public static void main(String[] args) throws InterruptedException {
        List<Integer> shared = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 10_000; i++) shared.add(i);

        Thread mutator = new Thread(() -> {
            for (int i = 0; i < 10_000; i++) {
                shared.add(99_999);
            }
        });

        Thread reader = new Thread(() -> {
            try {
                int sum = 0;
                for (int v : shared) {
                    sum += v;
                }
                System.out.println("reader finished, sum=" + sum);
            } catch (java.util.ConcurrentModificationException e) {
                System.out.println("CME during iteration — wrapping the list does not protect iteration");
            }
        });

        mutator.start();
        reader.start();
        mutator.join();
        reader.join();

        System.out.println();
        System.out.println("see SynchronizedListIterationLocked.java for the fix.");
    }
}

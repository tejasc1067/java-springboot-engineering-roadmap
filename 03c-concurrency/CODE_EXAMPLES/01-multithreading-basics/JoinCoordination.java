import java.util.ArrayList;
import java.util.List;

public class JoinCoordination {

    public static void main(String[] args) throws InterruptedException {
        List<Thread> workers = new ArrayList<>();
        int[] results = new int[4];

        for (int i = 0; i < 4; i++) {
            final int slot = i;
            Thread t = new Thread(() -> {
                try { Thread.sleep(100L * (slot + 1)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                results[slot] = (slot + 1) * (slot + 1);
            }, "worker-" + i);
            t.start();
            workers.add(t);
        }

        // Wait for each worker. Without these joins, main might print before
        // the workers wrote their results.
        for (Thread w : workers) w.join();

        int sum = 0;
        for (int r : results) sum += r;
        System.out.println("all workers done. sum of squares 1..4 = " + sum);
    }
}

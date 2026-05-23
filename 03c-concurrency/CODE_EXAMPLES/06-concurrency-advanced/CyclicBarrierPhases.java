import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class CyclicBarrierPhases {

    public static void main(String[] args) throws InterruptedException {
        int workers = 3;
        CyclicBarrier barrier = new CyclicBarrier(workers, () -> System.out.println("--- phase boundary ---"));

        Thread[] ts = new Thread[workers];
        for (int i = 0; i < workers; i++) {
            final int id = i;
            ts[i] = new Thread(() -> {
                try {
                    for (int phase = 1; phase <= 3; phase++) {
                        long ms = 50 + (long)(Math.random() * 100);
                        Thread.sleep(ms);
                        System.out.println("worker " + id + " finished phase " + phase);
                        barrier.await();    // wait for the other workers to reach the same point
                    }
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();
        System.out.println("all phases complete.");
    }
}

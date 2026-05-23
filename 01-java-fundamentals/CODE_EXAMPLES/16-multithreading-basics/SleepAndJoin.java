// Thread.sleep: pause the current thread for a duration.
// Thread.join:  block until another thread finishes.

public class SleepAndJoin {
    public static void main(String[] args) throws InterruptedException {

        Thread worker = new Thread(() -> {
            try {
                System.out.println("worker: starting");
                Thread.sleep(500);
                System.out.println("worker: half done");
                Thread.sleep(500);
                System.out.println("worker: done");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "worker");

        long t0 = System.currentTimeMillis();
        worker.start();
        System.out.println("main: worker started, doing other things");

        // Without the join, main would exit immediately and the worker
        // would still finish — but the program could terminate before the
        // worker's last print.
        worker.join();

        long elapsed = System.currentTimeMillis() - t0;
        System.out.println("main: worker finished after " + elapsed + "ms");
    }
}

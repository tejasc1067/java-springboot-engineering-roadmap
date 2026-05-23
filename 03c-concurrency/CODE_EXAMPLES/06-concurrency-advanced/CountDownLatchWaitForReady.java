import java.util.concurrent.CountDownLatch;

public class CountDownLatchWaitForReady {

    public static void main(String[] args) throws InterruptedException {
        int workerCount = 4;
        CountDownLatch ready = new CountDownLatch(workerCount);

        for (int i = 0; i < workerCount; i++) {
            final int id = i;
            new Thread(() -> {
                long ms = 50 + (long)(Math.random() * 200);
                try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                System.out.println("worker " + id + " ready after " + ms + " ms");
                ready.countDown();
            }).start();
        }

        System.out.println("main: waiting for all " + workerCount + " workers...");
        ready.await();
        System.out.println("main: all workers ready, proceeding.");
    }
}

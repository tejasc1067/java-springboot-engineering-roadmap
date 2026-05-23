import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockingQueueProducerConsumer {

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(5);
        String POISON_PILL = "__STOP__";

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    String task = "task-" + i;
                    queue.put(task);
                    System.out.println("produced " + task + " (queue size " + queue.size() + ")");
                    Thread.sleep(40);
                }
                queue.put(POISON_PILL);
            } catch (InterruptedException ignored) {}
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                while (true) {
                    String t = queue.take();
                    if (t.equals(POISON_PILL)) break;
                    System.out.println("           consumed " + t);
                    Thread.sleep(100);
                }
            } catch (InterruptedException ignored) {}
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println();
        System.out.println("Producer fills the queue, blocks on put when full (capacity 5).");
        System.out.println("Consumer takes one at a time, blocks on take when empty.");
        System.out.println("No wait/notify plumbing — the queue handles it.");
    }
}

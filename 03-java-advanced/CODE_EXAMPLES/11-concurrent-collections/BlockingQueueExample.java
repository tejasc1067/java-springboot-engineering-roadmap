import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BlockingQueueExample {

    public static void main(String[] args)
            throws InterruptedException {

        BlockingQueue<String> queue =
                new ArrayBlockingQueue<>(5);

        queue.put("Task-1");

        queue.put("Task-2");

        System.out.println(
                queue.take()
        );

        System.out.println(queue);
    }
}
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BackpressureSimulationExample {

    public static void main(String[] args)
            throws Exception {

        BlockingQueue<Integer> queue =
                new ArrayBlockingQueue<>(3);

        for (int i = 1; i <= 5; i++) {

            System.out.println(
                    "Producing: " + i
            );

            queue.put(i);

            System.out.println(
                    "Queue Size: "
                            + queue.size()
            );
        }
    }
}
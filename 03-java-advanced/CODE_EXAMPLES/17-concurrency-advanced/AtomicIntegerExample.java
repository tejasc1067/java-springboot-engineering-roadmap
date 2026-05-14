import java.util.concurrent.atomic.AtomicInteger;

public class AtomicIntegerExample {

    public static void main(String[] args)
            throws InterruptedException {

        AtomicInteger counter =
                new AtomicInteger(0);

        Runnable task = () -> {

            for (int index = 0;
                 index < 1000;
                 index++) {

                counter.incrementAndGet();
            }
        };

        Thread threadOne =
                new Thread(task);

        Thread threadTwo =
                new Thread(task);

        threadOne.start();

        threadTwo.start();

        threadOne.join();

        threadTwo.join();

        System.out.println(
                "Final Count: "
                        + counter.get()
        );
    }
}
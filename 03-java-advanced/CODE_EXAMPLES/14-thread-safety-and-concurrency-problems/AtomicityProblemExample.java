class Counter {

    int count = 0;

    void increment() {

        count++;
    }
}

public class AtomicityProblemExample {

    public static void main(String[] args)
            throws InterruptedException {

        Counter counter =
                new Counter();

        Runnable task = () -> {

            for (int index = 0;
                 index < 1000;
                 index++) {

                counter.increment();
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
                "Count: "
                        + counter.count
        );
    }
}
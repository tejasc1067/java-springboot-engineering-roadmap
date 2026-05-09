class SharedCounter {

    int count = 0;

    void increment() {

        count++;
    }
}

public class RaceConditionExample {

    public static void main(String[] args)
            throws InterruptedException {

        SharedCounter counter = new SharedCounter();

        Thread thread1 = new Thread(() -> {

            for (int i = 0; i < 1000; i++) {

                counter.increment();
            }
        });

        Thread thread2 = new Thread(() -> {

            for (int i = 0; i < 1000; i++) {

                counter.increment();
            }
        });

        thread1.start();

        thread2.start();

        thread1.join();

        thread2.join();

        System.out.println(
                "Final Count: "
                        + counter.count
        );
    }
}
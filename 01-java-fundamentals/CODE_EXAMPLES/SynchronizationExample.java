class Counter {

    private int count = 0;

    // Synchronized method
    synchronized void increment() {

        count++;
    }

    int getCount() {

        return count;
    }
}

public class SynchronizationExample {

    public static void main(String[] args)
            throws InterruptedException {

        Counter counter = new Counter();

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
                        + counter.getCount()
        );
    }
}
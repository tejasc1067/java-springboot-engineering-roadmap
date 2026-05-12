class ApiRequestCounter {

    private int requestCount = 0;

    synchronized void increment() {

        requestCount++;
    }

    synchronized int getRequestCount() {

        return requestCount;
    }
}

public class BackendCounterExample {

    public static void main(String[] args)
            throws InterruptedException {

        ApiRequestCounter counter =
                new ApiRequestCounter();

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
                "Total Requests: "
                        + counter.getRequestCount()
        );
    }
}
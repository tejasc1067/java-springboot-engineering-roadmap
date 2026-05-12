class SharedFlag {

    volatile boolean running = true;
}

public class VolatileKeywordExample {

    public static void main(String[] args)
            throws InterruptedException {

        SharedFlag flag =
                new SharedFlag();

        Thread worker =
                new Thread(() -> {

                    while (flag.running) {

                        // Waiting
                    }

                    System.out.println(
                            "Worker stopped"
                    );
                });

        worker.start();

        Thread.sleep(2000);

        flag.running = false;
    }
}
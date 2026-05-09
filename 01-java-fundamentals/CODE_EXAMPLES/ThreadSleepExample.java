public class ThreadSleepExample {

    public static void main(String[] args)
            throws InterruptedException {

        for (int i = 1; i <= 5; i++) {

            System.out.println(
                    "Processing Step: " + i
            );

            // Pause thread for 1 second
            Thread.sleep(1000);
        }

        System.out.println("Processing Completed");
    }
}
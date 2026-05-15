public class ThreadDumpSimulationExample {

    public static void main(String[] args) {

        Runnable task = () -> {

            while (true) {

                try {

                    Thread.sleep(1000);

                } catch (InterruptedException e) {

                    e.printStackTrace();
                }
            }
        };

        Thread thread1 =
                new Thread(task);

        Thread thread2 =
                new Thread(task);

        thread1.start();

        thread2.start();

        System.out.println(
                "Threads Running"
        );
    }
}
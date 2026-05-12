class DemoTask
        implements Runnable {

    @Override
    public void run() {

        System.out.println(
                "Executed by: "
                        + Thread.currentThread().getName()
        );
    }
}

public class StartVsRunExample {

    public static void main(String[] args) {

        Thread thread =
                new Thread(new DemoTask());

        // Creates new thread

        thread.start();

        // Normal method call

        thread.run();
    }
}
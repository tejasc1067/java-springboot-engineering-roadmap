class Task
        implements Runnable {

    @Override
    public void run() {

        System.out.println(
                "Runnable task executed by: "
                        + Thread.currentThread().getName()
        );
    }
}

public class RunnableExample {

    public static void main(String[] args) {

        Thread thread =
                new Thread(new Task());

        thread.start();
    }
}
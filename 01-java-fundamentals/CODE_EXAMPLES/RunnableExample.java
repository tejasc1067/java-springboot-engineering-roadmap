class Task implements Runnable {

    @Override
    public void run() {

        System.out.println(
                "Runnable Task Executed by: "
                        + Thread.currentThread().getName()
        );
    }
}

public class RunnableExample {

    public static void main(String[] args) {

        Thread thread1 = new Thread(new Task());

        Thread thread2 = new Thread(new Task());

        thread1.start();

        thread2.start();
    }
}
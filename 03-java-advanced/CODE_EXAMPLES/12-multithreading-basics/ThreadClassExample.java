class WorkerThread extends Thread {

    @Override
    public void run() {

        System.out.println(
                "Thread is running: "
                        + Thread.currentThread().getName()
        );
    }
}

public class ThreadClassExample {

    public static void main(String[] args) {

        WorkerThread workerThread =
                new WorkerThread();

        workerThread.start();
    }
}
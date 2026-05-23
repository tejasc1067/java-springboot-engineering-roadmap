// Two ways to create a thread. Runnable (or just a lambda) is the preferred
// style — it separates "what to do" from "how to run it."

public class ThreadCreation {

    // Style 1: extend Thread
    static class MyThread extends Thread {
        @Override public void run() {
            System.out.println("style 1: " + getName());
        }
    }

    public static void main(String[] args) throws InterruptedException {

        // Style 1
        Thread t1 = new MyThread();
        t1.start();

        // Style 2: Runnable
        Runnable task = () -> System.out.println("style 2: " + Thread.currentThread().getName());
        Thread t2 = new Thread(task, "worker-2");
        t2.start();

        // Style 2, inline lambda
        new Thread(() -> System.out.println("style 2 inline: "
                + Thread.currentThread().getName()), "worker-inline").start();

        // Important: t.start() schedules the thread to run. t.run() would
        // just execute the body synchronously on this thread.

        // Wait for them all so main doesn't exit before they print.
        t1.join();
        t2.join();
    }
}

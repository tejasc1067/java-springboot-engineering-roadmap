public class RunnableVsThread {

    public static void main(String[] args) throws InterruptedException {
        // Option A: Runnable (preferred). Task is separate from worker.
        Runnable task = () -> System.out.println("Runnable task on " + Thread.currentThread().getName());
        Thread workerA = new Thread(task, "worker-A");
        workerA.start();

        // Option B: extend Thread (legacy). Task is fused to the worker.
        Thread workerB = new MyThread("worker-B");
        workerB.start();

        workerA.join();
        workerB.join();
        System.out.println("main done");
    }

    static class MyThread extends Thread {
        MyThread(String name) { super(name); }
        public void run() {
            System.out.println("Thread subclass on " + Thread.currentThread().getName());
        }
    }
}

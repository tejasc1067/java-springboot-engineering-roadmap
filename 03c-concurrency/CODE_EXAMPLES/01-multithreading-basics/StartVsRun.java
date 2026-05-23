public class StartVsRun {

    public static void main(String[] args) throws InterruptedException {
        Runnable printSelf = () -> System.out.println("running on " + Thread.currentThread().getName());

        Thread t1 = new Thread(printSelf, "worker-1");
        System.out.print("t1.run()   -> ");
        t1.run();   // direct method call. Runs on main. No new thread.

        Thread t2 = new Thread(printSelf, "worker-2");
        System.out.print("t2.start() -> ");
        t2.start(); // asks the OS for a new thread, which then invokes run().
        t2.join();

        // Calling start() twice on the same Thread is illegal.
        try {
            t2.start();
        } catch (IllegalThreadStateException e) {
            System.out.println("second start() threw: " + e.getClass().getSimpleName());
        }
    }
}

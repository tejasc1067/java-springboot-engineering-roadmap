public class VisibilityProblem {

    static boolean running = true;    // NOT volatile

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long spins = 0;
            while (running) {        // may never observe main's write
                spins++;
            }
            System.out.println("worker stopped after " + spins + " spins");
        }, "spinner");

        worker.start();
        Thread.sleep(500);
        System.out.println("main: setting running = false");
        running = false;

        worker.join(2000);
        if (worker.isAlive()) {
            System.out.println("worker is STILL spinning -- visibility bug.");
            System.out.println("see VisibilityFixedVolatile.java for the fix.");
            worker.interrupt();
            System.exit(0);    // worker may never see the flag; just exit
        } else {
            System.out.println("worker observed the write THIS RUN.");
            System.out.println("This is not guaranteed -- behavior is undefined without volatile.");
        }
    }
}

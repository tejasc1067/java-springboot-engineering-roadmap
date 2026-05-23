public class VisibilityFixedVolatile {

    static volatile boolean running = true;    // each read goes to main memory

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long spins = 0;
            while (running) {
                spins++;
            }
            System.out.println("worker stopped after " + spins + " spins");
        }, "spinner");

        worker.start();
        Thread.sleep(500);
        System.out.println("main: setting running = false");
        running = false;

        worker.join(2000);
        System.out.println("worker alive after join? " + worker.isAlive());
    }
}

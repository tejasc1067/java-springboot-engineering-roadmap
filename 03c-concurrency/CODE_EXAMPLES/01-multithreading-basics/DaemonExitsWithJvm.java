public class DaemonExitsWithJvm {

    public static void main(String[] args) throws InterruptedException {
        Thread daemon = new Thread(() -> {
            // Infinite loop. Would keep the JVM alive forever if non-daemon.
            while (true) {
                System.out.println("daemon tick at " + System.currentTimeMillis());
                try { Thread.sleep(100); } catch (InterruptedException e) { return; }
            }
        }, "background-heartbeat");

        daemon.setDaemon(true);  // must be set BEFORE start()
        daemon.start();

        Thread.sleep(350);
        System.out.println("main finishing -- JVM will exit even though daemon is still looping");
    }
}

public class LifecycleStates {

    public static void main(String[] args) throws InterruptedException {
        Thread t = new Thread(() -> {
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "lifecycle-demo");

        // NEW: created but start() not called yet.
        System.out.println("before start():       " + t.getState());

        t.start();

        // RUNNABLE almost immediately. Briefly the OS may not have scheduled it,
        // but Java reports RUNNABLE either way.
        System.out.println("right after start():  " + t.getState());

        Thread.sleep(50);
        // While the worker is inside Thread.sleep(200) it should be TIMED_WAITING.
        System.out.println("while sleeping:       " + t.getState());

        t.join();
        // After run() returns the thread is TERMINATED. Cannot be restarted.
        System.out.println("after join():         " + t.getState());
    }
}

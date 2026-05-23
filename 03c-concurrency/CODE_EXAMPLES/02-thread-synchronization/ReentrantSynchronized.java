public class ReentrantSynchronized {

    private int n;

    // outer() already holds `this`. It then calls inner(), which is ALSO synchronized
    // on `this`. Because Java's intrinsic locks are reentrant, the same thread can
    // re-acquire its own lock without blocking itself.
    public synchronized void outer() {
        System.out.println(Thread.currentThread().getName() + " entered outer (lock held once)");
        inner();
        System.out.println(Thread.currentThread().getName() + " leaving outer");
    }

    public synchronized void inner() {
        n++;
        System.out.println(Thread.currentThread().getName() + " entered inner (lock held twice, n=" + n + ")");
    }

    public static void main(String[] args) throws InterruptedException {
        ReentrantSynchronized r = new ReentrantSynchronized();
        r.outer();
        // A non-reentrant lock would deadlock at the inner() call. synchronized doesn't.
    }
}

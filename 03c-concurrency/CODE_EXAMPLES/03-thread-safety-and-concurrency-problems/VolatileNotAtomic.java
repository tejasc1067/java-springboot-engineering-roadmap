public class VolatileNotAtomic {

    static volatile int counter = 0;     // volatile, yet still racy

    public static void main(String[] args) throws InterruptedException {
        int threads = 8, perThread = 200_000;
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) counter++;     // read-add-write
            });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();

        int expected = threads * perThread;
        System.out.println("expected: " + expected);
        System.out.println("actual:   " + counter);
        System.out.println("lost:     " + (expected - counter));
        System.out.println();
        System.out.println("volatile guarantees visibility, not atomicity.");
        System.out.println("see AtomicIntegerCounter.java for the right fix.");
    }
}

public class RaceConditionBroken {

    static long balance = 0;

    public static void main(String[] args) throws InterruptedException {
        int threads = 8;
        int perThread = 1_000_000;

        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                for (int j = 0; j < perThread; j++) {
                    balance += 1;   // read-modify-write -- NOT atomic
                }
            });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();

        long expected = (long) threads * perThread;
        System.out.println("expected: " + expected);
        System.out.println("actual:   " + balance);
        System.out.println("lost increments: " + (expected - balance));
        System.out.println();
        System.out.println("see SynchronizedFixed.java for the fix.");
    }
}

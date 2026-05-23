// The race in RaceConditionBroken.java fixed with `synchronized`. Only one
// thread can be inside `increment()` at a time, so the read-modify-write
// sequence is atomic.
//
// Cost: contention. Threads queue up at the lock.

public class SynchronizedFixed {

    static int count = 0;

    static synchronized void increment() {
        count++;
    }

    public static void main(String[] args) throws InterruptedException {

        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                increment();
            }
        };

        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("expected: 200000");
        System.out.println("actual:   " + count);
    }
}

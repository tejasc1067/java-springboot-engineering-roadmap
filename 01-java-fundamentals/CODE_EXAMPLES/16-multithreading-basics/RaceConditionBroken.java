// Two threads increment the same int. The expected total is 200,000 but the
// actual answer is almost always less — usually a "random" number in the
// 100,000 to 200,000 range.
//
// Why? count++ is read-modify-write. Threads interleave and overwrite each
// other's reads. Some increments are lost.

public class RaceConditionBroken {

    static int count = 0;

    public static void main(String[] args) throws InterruptedException {

        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                count++;            // not atomic
            }
        };

        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("expected: 200000");
        System.out.println("actual:   " + count + "   (run again — you'll get a different number)");
    }
}

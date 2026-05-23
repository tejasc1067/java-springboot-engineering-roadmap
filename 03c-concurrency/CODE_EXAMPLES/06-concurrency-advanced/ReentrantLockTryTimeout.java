import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockTryTimeout {

    static final ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException {
        Thread hog = new Thread(() -> {
            lock.lock();
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { lock.unlock(); }
        }, "hog");
        hog.start();
        Thread.sleep(50);   // ensure hog has the lock

        boolean got = lock.tryLock(100, TimeUnit.MILLISECONDS);
        System.out.println("tryLock(100ms) while hog holds it: " + got);

        hog.join();
        got = lock.tryLock(100, TimeUnit.MILLISECONDS);
        System.out.println("tryLock(100ms) after hog released:  " + got);
        if (got) lock.unlock();

        System.out.println();
        System.out.println("Unlike synchronized, tryLock lets you BOUND how long you wait.");
    }
}

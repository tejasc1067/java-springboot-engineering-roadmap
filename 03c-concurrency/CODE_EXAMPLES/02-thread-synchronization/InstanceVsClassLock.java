public class InstanceVsClassLock {

    // BUG: static field, but the lock is the instance monitor.
    // Two threads on two DIFFERENT instances hold DIFFERENT locks and both write `globalCount`.
    static class WrongScope {
        private static int globalCount = 0;
        public synchronized void bump() { globalCount++; }   // locks `this`, not the class
    }

    // FIXED: static synchronized locks the Class object -- one lock for ALL instances.
    static class RightScope {
        private static int globalCount = 0;
        public static synchronized void bump() { globalCount++; }
    }

    public static void main(String[] args) throws InterruptedException {
        WrongScope a = new WrongScope();
        WrongScope b = new WrongScope();
        int per = 1_000_000;
        Thread t1 = new Thread(() -> { for (int i = 0; i < per; i++) a.bump(); });
        Thread t2 = new Thread(() -> { for (int i = 0; i < per; i++) b.bump(); });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("WrongScope expected: " + (2 * per) + "  actual: " + WrongScope.globalCount);

        Thread t3 = new Thread(() -> { for (int i = 0; i < per; i++) RightScope.bump(); });
        Thread t4 = new Thread(() -> { for (int i = 0; i < per; i++) RightScope.bump(); });
        t3.start(); t4.start(); t3.join(); t4.join();
        System.out.println("RightScope expected: " + (2 * per) + "  actual: " + RightScope.globalCount);
    }
}

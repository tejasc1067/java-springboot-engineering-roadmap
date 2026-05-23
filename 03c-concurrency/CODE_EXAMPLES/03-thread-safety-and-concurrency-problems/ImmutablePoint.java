public final class ImmutablePoint {

    private final int x;
    private final int y;

    public ImmutablePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() { return x; }
    public int y() { return y; }

    // "Mutation" returns a new instance. The original is never changed.
    public ImmutablePoint translate(int dx, int dy) {
        return new ImmutablePoint(x + dx, y + dy);
    }

    public String toString() { return "(" + x + ", " + y + ")"; }

    public static void main(String[] args) throws InterruptedException {
        ImmutablePoint shared = new ImmutablePoint(0, 0);
        int threads = 8;
        Thread[] ts = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                // Every thread observes the same x=0,y=0. They can't corrupt it.
                ImmutablePoint local = shared.translate(1, 1);
                System.out.println(Thread.currentThread().getName() + " read " + shared + ", made " + local);
            });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();

        System.out.println("shared is still " + shared + " -- never mutated.");
    }
}

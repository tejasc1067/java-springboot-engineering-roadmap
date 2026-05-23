import java.lang.ref.WeakReference;

public class WeakReferenceCleared {

    static class Large {
        byte[] payload = new byte[1024 * 1024];   // 1 MB
        @Override public String toString() { return "Large#" + Integer.toHexString(System.identityHashCode(this)); }
    }

    public static void main(String[] args) throws InterruptedException {
        Large strong = new Large();
        WeakReference<Large> weak = new WeakReference<>(strong);

        System.out.println("before drop: weak.get() = " + weak.get());

        // Drop the only strong reference. The Large object is now weakly reachable.
        strong = null;

        // Hint the GC. Not guaranteed, but usually enough to demonstrate.
        for (int i = 0; i < 5 && weak.get() != null; i++) {
            System.gc();
            Thread.sleep(50);
        }

        System.out.println("after drop + GC: weak.get() = " + weak.get());
        if (weak.get() == null) {
            System.out.println("the object was reclaimed (weak ref cleared).");
        } else {
            System.out.println("(some JVMs keep it briefly; rerun if needed.)");
        }
    }
}

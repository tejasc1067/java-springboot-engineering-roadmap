// When the last reference to an object goes away, the object is eligible for
// garbage collection. The GC decides when to actually reclaim it — usually
// not immediately. System.gc() is a HINT, not a guarantee.

public class GcEligibility {

    public static void main(String[] args) {

        // Object 1 — reachable from local variable p1
        Object p1 = new Object();
        System.out.println("p1 created: " + System.identityHashCode(p1));

        // Object 2 — reachable from p2, which is then nulled
        Object p2 = new Object();
        int id2 = System.identityHashCode(p2);
        System.out.println("p2 created: " + id2);

        p2 = null;
        System.out.println("p2 = null   -> the original Object is now unreachable");

        // Suggest a GC. The runtime may or may not run one.
        System.gc();
        System.out.println("System.gc() requested (no guarantee anything ran)");

        // Reachable via two refs — only eligible when both go away.
        Object p3 = new Object();
        Object p4 = p3;
        p3 = null;          // still reachable via p4
        System.out.println("p3=null but p4 still refs the same object -> NOT eligible");
        p4 = null;          // now eligible
        System.out.println("p4=null too -> NOW eligible");
    }
}

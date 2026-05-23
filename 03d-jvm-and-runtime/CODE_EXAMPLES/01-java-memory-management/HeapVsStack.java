public class HeapVsStack {

    static String shared;    // static field -> reference is a GC root, object stays alive

    public static void main(String[] args) {
        shared = build(42);
        // After build() returns, its stack frame is gone.
        // But `shared` (a GC root) still references the String object on the heap.
        System.out.println("after build returns, shared = " + shared);
    }

    static String build(int n) {
        int local = n * 2;                     // primitive: lives on this stack frame
        String s = "value-" + local;           // reference `s`: stack. String object: heap.
        System.out.println("inside build: local=" + local + ", s identityHash=" + System.identityHashCode(s));
        return s;
        // local and `s` (the reference) vanish here.
        // The String OBJECT survives because `main` assigns it to `shared`.
    }
}

// Java is always pass-by-value. For objects, the *value* being copied is the
// reference. So:
//   - You CAN mutate the object the caller passed in (you share the object).
//   - You CANNOT make the caller's variable point to a different object.

public class PassByValue {

    public static void main(String[] args) {

        // Primitive — the callee gets a copy, can't affect the caller.
        int n = 5;
        increment(n);
        System.out.println("after increment(n): n = " + n + "   (still 5)");

        // Object — the callee mutates the shared object.
        StringBuilder sb = new StringBuilder("hi");
        appendBang(sb);
        System.out.println("after appendBang(sb): sb = " + sb + "   (now hi!)");

        // Object — the callee tries to "replace" with a new object. The caller
        // still sees the original.
        StringBuilder sb2 = new StringBuilder("hi");
        replaceWithGoodbye(sb2);
        System.out.println("after replaceWithGoodbye(sb2): sb2 = " + sb2 + "   (still hi)");

        // The right way to "replace": return the new value from the method.
        sb2 = returnNewBuilder();
        System.out.println("after sb2 = returnNewBuilder(): sb2 = " + sb2);
    }

    static void increment(int x) {
        x = x + 1;             // local x; caller's n unaffected
    }

    static void appendBang(StringBuilder s) {
        s.append("!");         // mutates the SHARED object
    }

    static void replaceWithGoodbye(StringBuilder s) {
        s = new StringBuilder("goodbye");  // local rebinding; caller can't see this
    }

    static StringBuilder returnNewBuilder() {
        return new StringBuilder("goodbye");
    }
}

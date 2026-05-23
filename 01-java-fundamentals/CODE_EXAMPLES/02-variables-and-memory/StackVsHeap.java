// The single most important Java behavior to internalize early:
// primitive assignment COPIES the value; reference assignment COPIES the reference.
// The object itself is never duplicated by `=`.

public class StackVsHeap {
    public static void main(String[] args) {

        // -------- Primitives: value semantics --------
        int a = 5;
        int b = a;       // b gets its own copy of the value 5
        b = 10;          // mutating b leaves a untouched
        System.out.println("Primitive copy:");
        System.out.println("  a = " + a + "   (still 5)");
        System.out.println("  b = " + b + "   (now 10)");

        // -------- References: shared object --------
        int[] x = {1, 2, 3};
        int[] y = x;     // y points to the SAME array as x
        y[0] = 99;       // mutating through y mutates the shared array
        System.out.println("\nReference share:");
        System.out.println("  x[0] = " + x[0] + "   (now 99, because x and y are the same array)");
        System.out.println("  y[0] = " + y[0]);
        System.out.println("  x == y: " + (x == y) + "   (same reference)");

        // -------- A real copy requires explicit work --------
        int[] z = x.clone();   // clone() makes a brand new array with the same contents
        z[0] = 0;
        System.out.println("\nAfter a real copy with clone():");
        System.out.println("  x[0] = " + x[0] + "   (still 99)");
        System.out.println("  z[0] = " + z[0] + "   (independent)");
        System.out.println("  x == z: " + (x == z) + "   (different references)");

        // Picture in memory:
        //
        //   stack                       heap
        //   +-------+                   +--------------+
        //   | a = 5 |                   |              |
        //   | b =10 |                   |              |
        //   | x  ----------------->     | {99, 2, 3}   |
        //   | y  ----------------->     |   (same)     |
        //   | z  ----------------->     | {0, 2, 3}    |
        //   +-------+                   +--------------+
    }
}

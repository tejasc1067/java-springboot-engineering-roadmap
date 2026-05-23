// The standard java.util.Arrays toolbox — sort, copy, fill, equals, toString.
// These come up constantly.

import java.util.Arrays;

public class ArrayUtilities {

    public static void main(String[] args) {

        int[] a = {3, 1, 4, 1, 5, 9, 2, 6};

        // toString — the only reasonable way to print an array
        System.out.println("original: " + Arrays.toString(a));

        // sort in place
        int[] b = a.clone();
        Arrays.sort(b);
        System.out.println("sorted:   " + Arrays.toString(b));

        // copyOf — make an independent copy, optionally with a different length
        int[] c = Arrays.copyOf(a, 5);                  // truncated
        int[] d = Arrays.copyOf(a, a.length + 3);       // padded with zeros
        System.out.println("first 5:  " + Arrays.toString(c));
        System.out.println("padded:   " + Arrays.toString(d));

        // fill — set every element to a value
        int[] sevens = new int[5];
        Arrays.fill(sevens, 7);
        System.out.println("sevens:   " + Arrays.toString(sevens));

        // equals — value equality (not the same as `==`)
        int[] e = {1, 2, 3};
        int[] f = {1, 2, 3};
        System.out.println("\ne == f             : " + (e == f) + "   (different arrays)");
        System.out.println("Arrays.equals(e, f): " + Arrays.equals(e, f) + "   (same contents)");
    }
}

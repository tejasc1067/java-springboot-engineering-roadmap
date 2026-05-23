// `new T[n]` produces an array of `n` slots, each filled with type T's
// default value. For object types that's null — and that null is waiting to
// trip you up if you forget to assign before reading.

public class ArrayDefaults {

    public static void main(String[] args) {

        int[]     ints     = new int[3];
        double[]  doubles  = new double[3];
        boolean[] booleans = new boolean[3];
        String[]  strings  = new String[3];

        System.out.println("int[3]:     " + java.util.Arrays.toString(ints));
        System.out.println("double[3]:  " + java.util.Arrays.toString(doubles));
        System.out.println("boolean[3]: " + java.util.Arrays.toString(booleans));
        System.out.println("String[3]:  " + java.util.Arrays.toString(strings));

        // Calling a method on a null slot crashes:
        try {
            System.out.println(strings[0].length());
        } catch (NullPointerException e) {
            System.out.println("\nNPE on strings[0].length() — the slot was null");
        }
    }
}

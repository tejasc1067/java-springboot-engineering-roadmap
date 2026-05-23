// Basic try/catch, multiple catches, and the type hierarchy that determines
// which catch fires.

public class TryCatch {
    public static void main(String[] args) {

        // Single catch
        try {
            int[] arr = {1, 2, 3};
            int x = arr[5];                  // throws ArrayIndexOutOfBoundsException
            System.out.println(x);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("caught: " + e.getMessage());
        }

        // Multiple catches — most specific first
        try {
            String s = null;
            int n = s.length();              // NullPointerException
        } catch (NullPointerException e) {
            System.out.println("caught NPE: " + e.getClass().getSimpleName());
        } catch (RuntimeException e) {       // would catch other runtime types
            System.out.println("caught some RuntimeException");
        }

        // Multi-catch (one block handles several types)
        try {
            mayThrow(0);
        } catch (IllegalArgumentException | ArithmeticException e) {
            System.out.println("caught one of two types: " + e.getMessage());
        }

        // The exception object carries information.
        try {
            Integer.parseInt("not a number");
        } catch (NumberFormatException e) {
            System.out.println("\nException details:");
            System.out.println("  type    = " + e.getClass().getName());
            System.out.println("  message = " + e.getMessage());
            System.out.println("  first frame = " + e.getStackTrace()[0]);
        }
    }

    static void mayThrow(int n) {
        if (n == 0) throw new IllegalArgumentException("n cannot be zero");
        int r = 10 / n;
        System.out.println(r);
    }
}

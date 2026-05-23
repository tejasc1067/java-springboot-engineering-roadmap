// Every Java primitive type, declared, initialized, and printed.
// Also shows the *default values* primitives get when declared as instance
// fields (which Java auto-initializes) — different from local variables
// (which the compiler refuses to let you read uninitialized).

public class PrimitivesAndDefaults {

    // Instance fields — Java auto-initializes these to type defaults.
    static byte    defaultByte;
    static short   defaultShort;
    static int     defaultInt;
    static long    defaultLong;
    static float   defaultFloat;
    static double  defaultDouble;
    static char    defaultChar;
    static boolean defaultBoolean;

    public static void main(String[] args) {

        byte    b   = 100;
        short   s   = 30_000;
        int     i   = 2_147_000_000;        // close to int max (~2.1 billion)
        long    l   = 9_000_000_000L;       // the `L` is required — without it the literal is treated as int and won't fit
        float   f   = 3.14f;                // the `f` is required — by default decimal literals are double
        double  d   = 3.141592653589793;
        char    c   = 'A';
        boolean t   = true;

        System.out.println("Explicitly initialized values:");
        System.out.println("  byte    = " + b);
        System.out.println("  short   = " + s);
        System.out.println("  int     = " + i);
        System.out.println("  long    = " + l);
        System.out.println("  float   = " + f);
        System.out.println("  double  = " + d);
        System.out.println("  char    = " + c);
        System.out.println("  boolean = " + t);

        System.out.println("\nDefault values when declared as fields:");
        System.out.println("  byte    -> " + defaultByte);
        System.out.println("  short   -> " + defaultShort);
        System.out.println("  int     -> " + defaultInt);
        System.out.println("  long    -> " + defaultLong);
        System.out.println("  float   -> " + defaultFloat);
        System.out.println("  double  -> " + defaultDouble);
        System.out.println("  char    -> [" + defaultChar + "]   (the null char '\\u0000', invisible)");
        System.out.println("  boolean -> " + defaultBoolean);

        // Try uncommenting this and observe the compile error:
        //   int local;
        //   System.out.println(local);
        // The compiler refuses to read a local variable that wasn't assigned.
    }
}

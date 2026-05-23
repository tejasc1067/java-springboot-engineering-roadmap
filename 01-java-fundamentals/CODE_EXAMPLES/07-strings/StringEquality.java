// == compares references. .equals() compares contents. Always use .equals()
// for Strings (and any object with meaningful value equality).
//
// The "string pool" makes == look like it works for literals — don't be fooled.

import java.util.Objects;

public class StringEquality {

    public static void main(String[] args) {

        // Two literals — both interned, both point at the same pooled object.
        String a = "alice";
        String b = "alice";
        System.out.println("a == b           : " + (a == b));            // true (pooled)
        System.out.println("a.equals(b)      : " + a.equals(b));         // true

        // new String(...) FORCES a fresh object, bypassing the pool.
        String c = new String("alice");
        System.out.println("\na == c           : " + (a == c));          // false
        System.out.println("a.equals(c)      : " + a.equals(c));         // true

        // A string built at runtime is also a distinct object.
        String d = "ali" + readSuffix();   // not a constant — runtime concatenation
        System.out.println("\na == d           : " + (a == d));          // false
        System.out.println("a.equals(d)      : " + a.equals(d));         // true

        // Null-safe comparison — Objects.equals doesn't throw on null.
        String e = null;
        System.out.println("\nObjects.equals(a, e): " + Objects.equals(a, e));   // false
        System.out.println("Objects.equals(e, e): " + Objects.equals(e, e));     // true

        // Defensive idiom — put the literal on the left so a null `d` doesn't crash.
        boolean isAlice = "alice".equals(d);
        System.out.println("\"alice\".equals(d) : " + isAlice);
    }

    static String readSuffix() {
        return "ce";   // stand-in for input that can't be a compile-time constant
    }
}

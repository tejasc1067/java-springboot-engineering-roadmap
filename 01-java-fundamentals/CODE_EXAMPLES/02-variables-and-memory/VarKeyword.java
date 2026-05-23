// `var` (Java 10+) tells the compiler "figure out the type from the right side."
// The variable is still strictly typed — you just don't have to spell the type.
//
// Use it when the type is obvious from the right-hand side.
// Avoid it when readers will have to guess what the type ended up being.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class VarKeyword {
    public static void main(String[] args) {

        // Good uses: type is obvious from the right-hand side.
        var name = "alice";                                       // String
        var count = 42;                                           // int
        var users = new ArrayList<String>();                      // ArrayList<String>
        var caches = new HashMap<String, Map<String, Integer>>(); // saves a lot of typing

        users.add("alice");
        users.add("bob");

        System.out.println("name  = " + name + "   (type: String)");
        System.out.println("count = " + count + "   (type: int)");
        System.out.println("users = " + users + "   (type: ArrayList<String>)");
        System.out.println("caches has " + caches.size() + " entries");

        // Bad use: type is hidden. What does process() return? A reader has to
        // jump to the method definition to find out.
        var result = process();
        System.out.println("\nresult = " + result + "   (type? you have to look at process())");

        // `var` can't be used for:
        //   - fields
        //   - method parameters or return types
        //   - variables without an initializer (the compiler needs to infer something)
        //   - variables initialized to null (no type to infer)
        //
        // Try uncommenting:  var x = null;   // compile error
    }

    static int process() {
        return 100;
    }
}

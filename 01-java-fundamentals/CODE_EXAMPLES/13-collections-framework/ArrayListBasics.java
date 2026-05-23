// ArrayList is the everyday list. Always declare the variable as List
// (the interface), instantiate as ArrayList. Switch implementations later
// by changing one line.

import java.util.ArrayList;
import java.util.List;

public class ArrayListBasics {
    public static void main(String[] args) {

        List<String> names = new ArrayList<>();
        names.add("alice");
        names.add("bob");
        names.add("carol");

        System.out.println("list: " + names);

        // Read by index
        System.out.println("at 1: " + names.get(1));

        // Replace
        names.set(1, "BOB");
        System.out.println("after set(1, BOB): " + names);

        // Insert in the middle (slow on large lists — everything shifts)
        names.add(0, "ANN");
        System.out.println("after add(0, ANN): " + names);

        // Remove
        names.remove("ANN");
        System.out.println("after remove(ANN): " + names);

        // Common queries
        System.out.println("size:           " + names.size());
        System.out.println("isEmpty:        " + names.isEmpty());
        System.out.println("contains alice: " + names.contains("alice"));
        System.out.println("indexOf BOB:    " + names.indexOf("BOB"));

        // Iterate
        System.out.println("\niterate:");
        for (String n : names) {
            System.out.println("  " + n);
        }

        // Immutable list — useful for constants
        List<String> frozen = List.of("x", "y", "z");
        System.out.println("\nfrozen: " + frozen);
        try {
            frozen.add("w");
        } catch (UnsupportedOperationException e) {
            System.out.println("can't add to List.of() — it's immutable");
        }
    }
}

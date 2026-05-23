// HashSet: unique values, fast lookup, no ordering. Adding a duplicate is a
// no-op (returns false to tell you it was already there).

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public class HashSetUniqueness {
    public static void main(String[] args) {

        Set<String> seen = new HashSet<>();
        boolean added1 = seen.add("alice");
        boolean added2 = seen.add("bob");
        boolean added3 = seen.add("alice");      // duplicate
        System.out.println("first add 'alice': " + added1);
        System.out.println("add 'bob':         " + added2);
        System.out.println("second add 'alice': " + added3 + "   (false — already present)");
        System.out.println("contents: " + seen);

        // Fast membership test — O(1) average.
        System.out.println("contains alice? " + seen.contains("alice"));

        // HashSet has NO ordering guarantee. If you need insertion order:
        Set<String> ordered = new LinkedHashSet<>();
        ordered.add("c"); ordered.add("a"); ordered.add("b");
        System.out.println("\nLinkedHashSet (insertion order): " + ordered);

        // ...or sorted order:
        Set<String> sorted = new TreeSet<>();
        sorted.add("c"); sorted.add("a"); sorted.add("b");
        System.out.println("TreeSet       (sorted):          " + sorted);
    }
}

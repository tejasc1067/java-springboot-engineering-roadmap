// Removing from a list during a for-each loop = ConcurrentModificationException.
//
// The for-each loop uses an Iterator under the hood. When you call
// list.remove(...) directly, the iterator's internal counter no longer matches
// the list, and the next iteration call throws.
//
// Run this and watch it crash. The fix is in ConcurrentModificationFixed.java.

import java.util.ArrayList;
import java.util.List;

public class ConcurrentModificationBroken {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("alice", "bob", "carol", "dave"));

        for (String name : names) {
            if (name.startsWith("b") || name.startsWith("c")) {
                names.remove(name);   // boom
            }
        }

        System.out.println(names);
    }
}

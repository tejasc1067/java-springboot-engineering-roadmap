// Three ways to safely remove items from a list during iteration.

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ConcurrentModificationFixed {
    public static void main(String[] args) {

        // Approach 1: explicit Iterator + its own remove()
        List<String> a = new ArrayList<>(List.of("alice", "bob", "carol", "dave"));
        Iterator<String> it = a.iterator();
        while (it.hasNext()) {
            String name = it.next();
            if (name.startsWith("b") || name.startsWith("c")) {
                it.remove();   // safe: the iterator knows about this
            }
        }
        System.out.println("approach 1: " + a);

        // Approach 2: removeIf — concise, idiomatic in modern Java
        List<String> b = new ArrayList<>(List.of("alice", "bob", "carol", "dave"));
        b.removeIf(name -> name.startsWith("b") || name.startsWith("c"));
        System.out.println("approach 2: " + b);

        // Approach 3: iterate the original, build a new list of survivors
        List<String> c = new ArrayList<>(List.of("alice", "bob", "carol", "dave"));
        List<String> kept = new ArrayList<>();
        for (String name : c) {
            if (!(name.startsWith("b") || name.startsWith("c"))) {
                kept.add(name);
            }
        }
        System.out.println("approach 3: " + kept);
    }
}

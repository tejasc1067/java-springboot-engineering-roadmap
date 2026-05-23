// A small demo of "which collection should I pick?".
// Counting word frequencies uses HashMap.
// Deduplicating preserves uniqueness via HashSet.
// Asking "is X in this big collection?" gets dramatically faster with a Set.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChooseTheCollection {
    public static void main(String[] args) {

        // Word frequency — HashMap is the natural fit.
        String sentence = "the quick brown fox jumps over the lazy dog the fox is fast";
        Map<String, Integer> freq = new HashMap<>();
        for (String word : sentence.split(" ")) {
            freq.merge(word, 1, Integer::sum);    // shorter idiom for "increment-or-set"
        }
        System.out.println("frequencies: " + freq);

        // Dedup — feed everything into a Set.
        List<String> withDuplicates = List.of("alice", "bob", "alice", "carol", "bob");
        Set<String> unique = new HashSet<>(withDuplicates);
        System.out.println("\noriginal: " + withDuplicates);
        System.out.println("unique:   " + unique);

        // The "is X in this collection?" benchmark — Set wins by a lot once
        // the collection gets big.
        int size = 100_000;
        List<Integer> list = new ArrayList<>();
        Set<Integer>  set  = new HashSet<>();
        for (int i = 0; i < size; i++) {
            list.add(i);
            set.add(i);
        }

        int needle = size - 1;
        int iterations = 1_000;

        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) list.contains(needle);
        long t2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) set.contains(needle);
        long t3 = System.nanoTime();

        System.out.println("\nList.contains x " + iterations + ": " + ((t2 - t1) / 1_000_000) + " ms");
        System.out.println("Set.contains  x " + iterations + ": " + ((t3 - t2) / 1_000_000) + " ms");
    }
}

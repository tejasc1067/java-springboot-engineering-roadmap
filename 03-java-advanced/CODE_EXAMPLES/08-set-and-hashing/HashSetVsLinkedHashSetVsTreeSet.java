import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

public class HashSetVsLinkedHashSetVsTreeSet {

    public static void main(String[] args) {
        String[] words = {"banana", "apple", "cherry", "apple", "date", "banana"};

        Set<String> hashSet       = new HashSet<>();
        Set<String> linkedHashSet = new LinkedHashSet<>();
        Set<String> treeSet       = new TreeSet<>();

        for (String w : words) {
            hashSet.add(w);
            linkedHashSet.add(w);
            treeSet.add(w);
        }

        System.out.println("input order:    " + java.util.Arrays.toString(words));
        System.out.println("HashSet:        " + hashSet);
        System.out.println("LinkedHashSet:  " + linkedHashSet + "   (insertion order)");
        System.out.println("TreeSet:        " + treeSet       + "   (sorted)");

        System.out.println();
        System.out.println("All three deduplicate. Iteration order is the difference.");
    }
}

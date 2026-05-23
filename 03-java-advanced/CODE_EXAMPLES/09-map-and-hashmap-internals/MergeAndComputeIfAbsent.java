import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MergeAndComputeIfAbsent {

    public static void main(String[] args) {
        String[] words = {"apple", "banana", "apple", "cherry", "banana", "apple"};

        Map<String, Integer> oldStyle = new HashMap<>();
        for (String w : words) {
            if (oldStyle.containsKey(w)) {
                oldStyle.put(w, oldStyle.get(w) + 1);
            } else {
                oldStyle.put(w, 1);
            }
        }
        System.out.println("old style (containsKey + get + put): " + oldStyle);

        Map<String, Integer> merged = new HashMap<>();
        for (String w : words) {
            merged.merge(w, 1, Integer::sum);
        }
        System.out.println("merge:                                " + merged);

        Map<String, List<String>> byInitial = new HashMap<>();
        for (String w : words) {
            byInitial.computeIfAbsent(w.substring(0, 1), k -> new ArrayList<>()).add(w);
        }
        System.out.println("computeIfAbsent grouping:             " + byInitial);

        System.out.println();
        System.out.println("merge/computeIfAbsent do one hash lookup instead of containsKey + get + put.");
    }
}

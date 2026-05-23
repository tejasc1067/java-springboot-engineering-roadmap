import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ChooseTheRightCollection {

    public static void main(String[] args) {
        int N = 100_000;

        List<Integer> arrayList = new ArrayList<>();
        for (int i = 0; i < N; i++) arrayList.add(i);
        timeContains("ArrayList contains(99999)", arrayList, 99_999);

        LinkedList<Integer> linkedList = new LinkedList<>(arrayList);
        timeContains("LinkedList contains(99999)", linkedList, 99_999);

        HashSet<Integer> hashSet = new HashSet<>(arrayList);
        timeContains("HashSet contains(99999)", hashSet, 99_999);

        TreeSet<Integer> treeSet = new TreeSet<>(arrayList);
        timeContains("TreeSet contains(99999)", treeSet, 99_999);

        Map<String, Integer> map = new HashMap<>();
        map.put("a", 1); map.put("b", 2); map.put("c", 3);
        System.out.println();
        System.out.println("HashMap get(\"b\") = " + map.get("b"));

        System.out.println();
        System.out.println("ArrayList vs HashSet contains: the gap is the point of choosing right.");
    }

    static void timeContains(String label, java.util.Collection<Integer> c, int target) {
        long start = System.nanoTime();
        int iterations = 1000;
        boolean any = false;
        for (int i = 0; i < iterations; i++) {
            any |= c.contains(target);
        }
        long elapsedUs = (System.nanoTime() - start) / 1_000;
        System.out.println(label + " -- " + iterations + " lookups in " + elapsedUs + " us (found=" + any + ")");
    }
}

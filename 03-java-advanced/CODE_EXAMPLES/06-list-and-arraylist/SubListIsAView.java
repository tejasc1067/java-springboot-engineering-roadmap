import java.util.ArrayList;
import java.util.List;

public class SubListIsAView {

    public static void main(String[] args) {
        List<String> all = new ArrayList<>(List.of("A", "B", "C", "D", "E"));
        List<String> middle = all.subList(1, 4);

        System.out.println("all:    " + all);
        System.out.println("middle: " + middle);

        middle.set(0, "X");
        System.out.println();
        System.out.println("after middle.set(0, \"X\"):");
        System.out.println("all:    " + all + "   <-- parent was mutated");
        System.out.println("middle: " + middle);

        System.out.println();
        try {
            all.add("F");
            middle.size();
        } catch (java.util.ConcurrentModificationException e) {
            System.out.println("modifying parent invalidated the sublist view -> CME on next access");
        }

        System.out.println();
        List<String> independent = new ArrayList<>(all.subList(1, 4));
        all.set(2, "MUTATED");
        System.out.println("independent copy (parent later mutated): " + independent);
    }
}

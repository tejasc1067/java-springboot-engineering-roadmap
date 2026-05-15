import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CollectionPerformanceExample {

    public static void main(String[] args) {

        List<Integer> list =
                new ArrayList<>();

        Set<Integer> set =
                new HashSet<>();

        for (int index = 0;
             index < 100000;
             index++) {

            list.add(index);

            set.add(index);
        }

        long listStart =
                System.nanoTime();

        list.contains(99999);

        long listEnd =
                System.nanoTime();

        long setStart =
                System.nanoTime();

        set.contains(99999);

        long setEnd =
                System.nanoTime();

        System.out.println(
                "List Lookup Time: "
                        + (listEnd - listStart)
        );

        System.out.println(
                "Set Lookup Time: "
                        + (setEnd - setStart)
        );
    }
}
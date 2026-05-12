import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SetVsListExample {

    public static void main(String[] args) {

        List<String> list =
                new ArrayList<>();

        list.add("Java");

        list.add("Java");

        Set<String> set =
                new HashSet<>();

        set.add("Java");

        set.add("Java");

        System.out.println(
                "List: " + list
        );

        System.out.println(
                "Set: " + set
        );
    }
}
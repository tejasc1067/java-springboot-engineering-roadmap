import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OrderByExample {

    public static void main(String[] args) {

        List<String> names =
                new ArrayList<>();

        names.add("Rahul");
        names.add("Tejas");
        names.add("Amit");

        Collections.sort(names);

        System.out.println(names);
    }
}
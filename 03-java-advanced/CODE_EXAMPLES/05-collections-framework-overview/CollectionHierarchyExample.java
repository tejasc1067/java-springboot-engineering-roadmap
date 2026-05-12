import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CollectionHierarchyExample {

    public static void main(String[] args) {

        Collection<String> collection =
                new ArrayList<>();

        collection.add("Java");

        collection.add("Spring Boot");

        System.out.println(collection);

        List<String> list =
                new ArrayList<>();

        list.add("AWS");

        System.out.println(list);
    }
}
import java.util.ArrayList;
import java.util.Iterator;

public class IteratorExample {

    public static void main(String[] args) {

        ArrayList<String> technologies = new ArrayList<>();

        technologies.add("Java");

        technologies.add("Spring Boot");

        technologies.add("AWS");

        // Iterator traversal
        Iterator<String> iterator = technologies.iterator();

        while (iterator.hasNext()) {

            System.out.println(iterator.next());
        }
    }
}
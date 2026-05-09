import java.util.ArrayList;
import java.util.Collections;

public class CollectionSortingExample {

    public static void main(String[] args) {

        ArrayList<Integer> marks = new ArrayList<>();

        marks.add(85);

        marks.add(95);

        marks.add(70);

        marks.add(90);

        System.out.println("Before Sorting: " + marks);

        // Sorting collection
        Collections.sort(marks);

        System.out.println("After Sorting: " + marks);
    }
}
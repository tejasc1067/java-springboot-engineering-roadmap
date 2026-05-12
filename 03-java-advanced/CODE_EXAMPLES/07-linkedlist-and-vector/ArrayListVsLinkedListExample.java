import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ArrayListVsLinkedListExample {

    public static void main(String[] args) {

        List<String> arrayList =
                new ArrayList<>();

        List<String> linkedList =
                new LinkedList<>();

        arrayList.add("Fast Access");

        linkedList.add("Efficient Insertions");

        System.out.println(arrayList);

        System.out.println(linkedList);
    }
}
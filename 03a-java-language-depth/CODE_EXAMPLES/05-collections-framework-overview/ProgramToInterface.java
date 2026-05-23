import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ProgramToInterface {

    static int sumSizesInterface(List<String> list) {
        int total = 0;
        for (String s : list) total += s.length();
        return total;
    }

    static int sumSizesTooSpecific(ArrayList<String> list) {
        int total = 0;
        for (String s : list) total += s.length();
        return total;
    }

    public static void main(String[] args) {
        ArrayList<String> arrayList   = new ArrayList<>(List.of("apple", "banana"));
        LinkedList<String> linkedList = new LinkedList<>(List.of("cherry", "date"));

        System.out.println("interface form accepts ArrayList:  " + sumSizesInterface(arrayList));
        System.out.println("interface form accepts LinkedList: " + sumSizesInterface(linkedList));

        System.out.println();
        System.out.println("too-specific form accepts ArrayList:  " + sumSizesTooSpecific(arrayList));
        // sumSizesTooSpecific(linkedList);
        // ^ compile error -- LinkedList is not an ArrayList, even though both are List
        System.out.println("too-specific form REJECTS LinkedList -- caller is locked in.");
    }
}

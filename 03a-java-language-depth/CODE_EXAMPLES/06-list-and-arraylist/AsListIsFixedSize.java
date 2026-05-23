import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AsListIsFixedSize {

    public static void main(String[] args) {
        List<String> fromAsList = Arrays.asList("a", "b", "c");
        System.out.println("Arrays.asList contents: " + fromAsList);

        fromAsList.set(0, "X");
        System.out.println("set works:               " + fromAsList);

        try {
            fromAsList.add("d");
        } catch (UnsupportedOperationException e) {
            System.out.println("add throws -- Arrays.asList list is fixed-size");
        }

        List<String> mutable = new ArrayList<>(Arrays.asList("a", "b", "c"));
        mutable.add("d");
        System.out.println("new ArrayList<>(Arrays.asList(...)): " + mutable + "   (mutable copy)");

        List<String> immutable = List.of("a", "b", "c");
        try {
            immutable.set(0, "X");
        } catch (UnsupportedOperationException e) {
            System.out.println("List.of returns a fully immutable list -- set throws too");
        }
    }
}

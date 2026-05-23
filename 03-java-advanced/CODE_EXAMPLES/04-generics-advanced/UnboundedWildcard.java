import java.util.List;

public class UnboundedWildcard {

    static int sizeOfAny(List<?> list) {
        return list.size();
        // list.add("anything");   ← compile error: cannot add to ?
        //                           (except null)
    }

    static void printAsObjects(List<?> list) {
        for (Object o : list) {
            System.out.println("  - " + o);
        }
    }

    public static void main(String[] args) {
        System.out.println("size of List<String>:  " + sizeOfAny(List.of("a", "b", "c")));
        System.out.println("size of List<Integer>: " + sizeOfAny(List.of(1, 2)));
        System.out.println("size of List<Object>:  " + sizeOfAny(List.of(new Object(), new Object())));

        System.out.println();
        System.out.println("contents of a List<Double>:");
        printAsObjects(List.of(1.1, 2.2, 3.3));

        System.out.println();
        System.out.println("List<?> accepts any parameterization — List<Object> would not.");
    }
}

import java.util.List;

public class GenericMethod {

    public static <T> T firstOrNull(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    public static <T> void printAll(List<T> list) {
        for (T item : list) {
            System.out.println("  - " + item);
        }
    }

    public static void main(String[] args) {
        String s = firstOrNull(List.of("apple", "banana"));
        Integer i = firstOrNull(List.of(10, 20, 30));
        Object none = firstOrNull(List.of());

        System.out.println("first string: " + s);
        System.out.println("first int: " + i);
        System.out.println("first of empty: " + none);

        System.out.println();
        System.out.println("printAll on strings:");
        printAll(List.of("a", "b", "c"));

        System.out.println("printAll on integers:");
        printAll(List.of(1, 2, 3));
    }
}

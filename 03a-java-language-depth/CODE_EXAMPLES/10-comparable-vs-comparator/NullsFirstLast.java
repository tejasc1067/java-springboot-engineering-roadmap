import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NullsFirstLast {

    record User(String name, String nickname) {}

    public static void main(String[] args) {
        List<User> users = new ArrayList<>(List.of(
            new User("Alice",   "ally"),
            new User("Bob",     null),
            new User("Charlie", "chip"),
            new User("Dana",    null)
        ));

        Comparator<User> nullsLast = Comparator
            .comparing(User::nickname, Comparator.nullsLast(Comparator.naturalOrder()));
        List<User> copyLast = new ArrayList<>(users);
        copyLast.sort(nullsLast);
        System.out.println("nulls last:");
        copyLast.forEach(u -> System.out.println("  " + u));

        Comparator<User> nullsFirst = Comparator
            .comparing(User::nickname, Comparator.nullsFirst(Comparator.naturalOrder()));
        List<User> copyFirst = new ArrayList<>(users);
        copyFirst.sort(nullsFirst);
        System.out.println();
        System.out.println("nulls first:");
        copyFirst.forEach(u -> System.out.println("  " + u));
    }
}

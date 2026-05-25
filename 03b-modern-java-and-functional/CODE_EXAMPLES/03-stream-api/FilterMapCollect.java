import java.util.List;
import java.util.stream.Collectors;

public class FilterMapCollect {

    record User(String name, int age, String dept) {}

    public static void main(String[] args) {
        List<User> users = List.of(
            new User("Alice",   25, "Eng"),
            new User("Bob",     17, "Eng"),
            new User("Carol",   31, "Sales"),
            new User("Dave",    16, "Sales"),
            new User("Eve",     22, "Eng")
        );

        // Pipeline: adults in Eng, names, sorted, joined as a CSV.
        String result = users.stream()
            .filter(u -> u.age() >= 18)
            .filter(u -> u.dept().equals("Eng"))
            .map(User::name)
            .sorted()
            .collect(Collectors.joining(", "));

        System.out.println("adult Eng names: " + result);

        // Same idea, collecting to a list.
        List<String> names = users.stream()
            .filter(u -> u.age() >= 18)
            .map(User::name)
            .sorted()
            .collect(Collectors.toList());
        System.out.println("all adults:      " + names);
    }
}

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CollectorsToolkit {

    record User(String name, int age, String dept) {}

    public static void main(String[] args) {
        List<User> users = List.of(
            new User("Alice",  25, "Eng"),
            new User("Bob",    17, "Eng"),
            new User("Carol",  31, "Sales"),
            new User("Dave",   16, "Sales"),
            new User("Eve",    22, "Eng")
        );

        // toMap -- index by name.
        Map<String, Integer> ageByName = users.stream()
            .collect(Collectors.toMap(User::name, User::age));
        System.out.println("ageByName: " + ageByName);

        // toMap with merge function -- safe even when keys collide.
        Map<String, Integer> totalAgeByDept = users.stream()
            .collect(Collectors.toMap(User::dept, User::age, Integer::sum));
        System.out.println("totalAgeByDept: " + totalAgeByDept);

        // groupingBy -- bucket by key.
        Map<String, List<User>> byDept = users.stream()
            .collect(Collectors.groupingBy(User::dept));
        System.out.println("byDept: " + byDept);

        // groupingBy + counting -- group size.
        Map<String, Long> sizeByDept = users.stream()
            .collect(Collectors.groupingBy(User::dept, Collectors.counting()));
        System.out.println("sizeByDept: " + sizeByDept);

        // partitioningBy -- boolean predicate yields exactly two buckets (true/false).
        Map<Boolean, List<User>> adultSplit = users.stream()
            .collect(Collectors.partitioningBy(u -> u.age() >= 18));
        System.out.println("adults?  -> " + adultSplit.get(true).size());
        System.out.println("minors?  -> " + adultSplit.get(false).size());

        // joining -- the common CSV/HTML build.
        String csv = users.stream().map(User::name).collect(Collectors.joining(", "));
        String html = users.stream().map(User::name).collect(Collectors.joining(", ", "<ul>", "</ul>"));
        System.out.println("csv:  " + csv);
        System.out.println("html: " + html);
    }
}

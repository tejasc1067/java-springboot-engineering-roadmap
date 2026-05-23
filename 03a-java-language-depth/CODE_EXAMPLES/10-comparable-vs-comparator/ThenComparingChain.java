import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ThenComparingChain {

    record User(String status, LocalDate signupDate, String name) {}

    public static void main(String[] args) {
        List<User> users = new ArrayList<>(List.of(
            new User("active",   LocalDate.of(2024, 5, 1), "Charlie"),
            new User("inactive", LocalDate.of(2025, 1, 1), "Alice"),
            new User("active",   LocalDate.of(2024, 5, 1), "Alice"),
            new User("active",   LocalDate.of(2025, 3, 1), "Bob")
        ));

        Comparator<User> order = Comparator
            .comparing(User::status)
            .thenComparing(User::signupDate, Comparator.reverseOrder())
            .thenComparing(User::name);

        users.sort(order);

        System.out.println("sorted by status -> signupDate desc -> name:");
        users.forEach(u -> System.out.println("  " + u));

        System.out.println();
        System.out.println("Notice Alice/Charlie share status and date -- name breaks the tie.");
    }
}

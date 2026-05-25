import java.util.Optional;

public class OptionalConsumeBadVsGood {

    record User(long id, String name) {}

    static Optional<User> findById(long id) {
        return id == 1 ? Optional.of(new User(1, "alice")) : Optional.empty();
    }

    public static void main(String[] args) {
        // Anti-pattern: this is "if (u != null) ... else ..." with extra steps.
        Optional<User> u1 = findById(1);
        String badPresent;
        if (u1.isPresent()) {
            badPresent = u1.get().name();
        } else {
            badPresent = "unknown";
        }
        System.out.println("bad (present):  " + badPresent);

        // Idiomatic: declarative, no get(), no isPresent.
        String goodPresent = findById(1).map(User::name).orElse("unknown");
        String goodAbsent  = findById(999).map(User::name).orElse("unknown");
        System.out.println("good (present): " + goodPresent);
        System.out.println("good (absent):  " + goodAbsent);

        // Throwing variant for "must exist" cases.
        try {
            findById(999).orElseThrow(() -> new RuntimeException("user 999 missing"));
        } catch (RuntimeException e) {
            System.out.println("orElseThrow:   " + e.getMessage());
        }

        // Side-effect only on present.
        findById(1).ifPresent(found -> System.out.println("ifPresent:     hit -> " + found.name()));
        findById(999).ifPresent(found -> System.out.println("ifPresent:     would NOT print for 999"));

        // Different actions for present vs absent (Java 9+).
        findById(999).ifPresentOrElse(
            found -> System.out.println("hit:  " + found.name()),
            ()    -> System.out.println("miss for 999")
        );
    }
}

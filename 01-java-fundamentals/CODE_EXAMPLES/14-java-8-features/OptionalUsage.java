// Optional<T> says "this might have a value or might not." It's the modern
// alternative to returning null for "not found." Use it as a return type;
// don't use it as a field or parameter.

import java.util.List;
import java.util.Optional;

public class OptionalUsage {

    record User(String name, int age) { }

    static List<User> users = List.of(
            new User("alice", 30),
            new User("bob", 25),
            new User("carol", 28)
    );

    static Optional<User> findByName(String name) {
        return users.stream().filter(u -> u.name().equals(name)).findFirst();
    }

    public static void main(String[] args) {

        // The wrong way — get() on an empty Optional throws.
        try {
            User missing = findByName("dave").get();    // NoSuchElementException
            System.out.println("found: " + missing);
        } catch (Exception e) {
            System.out.println("don't do this: " + e.getClass().getSimpleName());
        }

        // The right way #1 — orElse with a sentinel default.
        User u1 = findByName("dave").orElse(new User("guest", 0));
        System.out.println("with orElse: " + u1);

        // The right way #2 — orElseThrow with a custom exception.
        try {
            User u2 = findByName("dave").orElseThrow(
                    () -> new IllegalStateException("user not found: dave"));
            System.out.println(u2);
        } catch (IllegalStateException e) {
            System.out.println("with orElseThrow: " + e.getMessage());
        }

        // The right way #3 — ifPresent only runs the block when there's a value.
        findByName("alice").ifPresent(u -> System.out.println("found alice: " + u));
        findByName("dave").ifPresent(u -> System.out.println("would not print"));

        // The right way #4 — map / chain.
        String displayName = findByName("alice")
                .map(User::name)
                .map(String::toUpperCase)
                .orElse("UNKNOWN");
        System.out.println("display: " + displayName);
    }
}

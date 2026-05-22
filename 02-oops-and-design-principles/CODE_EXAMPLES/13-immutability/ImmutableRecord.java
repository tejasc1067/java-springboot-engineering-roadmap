// Records are Java's shortcut for immutable value classes.
// `record User(String name, int age)` generates final class, final fields,
// constructor, accessors, equals, hashCode, toString — all immutable by design.
//
// The "compact constructor" form lets you validate inputs without re-writing
// the assignments.

public record ImmutableRecord(String name, int age) {

    // Compact constructor — runs after Java has built the field assignments.
    public ImmutableRecord {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (age < 0)                        throw new IllegalArgumentException("age cannot be negative");
    }

    // Extra methods are allowed.
    public ImmutableRecord withAge(int newAge) {
        return new ImmutableRecord(name, newAge);
    }

    public static void main(String[] args) {
        ImmutableRecord alice = new ImmutableRecord("Alice", 30);
        System.out.println("alice = " + alice);                 // User[name=Alice, age=30]

        ImmutableRecord olderAlice = alice.withAge(31);
        System.out.println("older = " + olderAlice);

        // Validation kicks in.
        try {
            new ImmutableRecord("", 30);
        } catch (IllegalArgumentException e) {
            System.out.println("\nRejected: " + e.getMessage());
        }

        // Records auto-generate equals/hashCode/toString.
        ImmutableRecord alice2 = new ImmutableRecord("Alice", 30);
        System.out.println("alice.equals(alice2): " + alice.equals(alice2));   // true
    }
}

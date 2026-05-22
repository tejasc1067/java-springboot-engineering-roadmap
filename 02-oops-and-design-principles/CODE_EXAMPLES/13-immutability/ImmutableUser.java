// The four rules:
//   1. final class — no subclass can sneak mutable state in.
//   2. private final fields.
//   3. No setters.
//   4. (Fields here are immutable types, so no defensive copy needed.
//       For mutable types like List, see DefensiveCopyMutableField.java.)
//
// To "modify" the user, call withAge(...) which returns a NEW User.

import java.util.Objects;

public final class ImmutableUser {

    private final String name;
    private final int age;

    public ImmutableUser(String name, int age) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (age < 0)                        throw new IllegalArgumentException("age cannot be negative");
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public int    getAge()  { return age; }

    public ImmutableUser withAge(int newAge) {
        return new ImmutableUser(this.name, newAge);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ImmutableUser u)) return false;
        return age == u.age && Objects.equals(name, u.name);
    }
    @Override public int hashCode() { return Objects.hash(name, age); }
    @Override public String toString() { return "User{name=" + name + ", age=" + age + "}"; }

    public static void main(String[] args) {
        ImmutableUser alice = new ImmutableUser("Alice", 30);
        ImmutableUser olderAlice = alice.withAge(31);

        System.out.println("alice      = " + alice);            // Alice, 30
        System.out.println("olderAlice = " + olderAlice);       // Alice, 31
        System.out.println("alice unchanged? " + (alice.getAge() == 30));   // true

        // No setter exists. The reference is final. The field is final.
        // alice.age = 99;   // ← won't compile
    }
}

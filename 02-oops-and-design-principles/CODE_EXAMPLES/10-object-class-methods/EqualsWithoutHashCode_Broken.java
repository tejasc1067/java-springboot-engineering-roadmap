// Override equals but not hashCode — the classic bug. The two Alices ARE equal
// by our equals(), but the HashSet can't tell, because it uses hashCode to
// look up buckets.
//
// COMPARE WITH: EqualsAndHashCodeProper.java

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class User {
    private final String name;
    private final int age;

    User(String name, int age) { this.name = name; this.age = age; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return age == other.age && Objects.equals(name, other.name);
    }

    // Deliberately NOT overriding hashCode — using the default from Object.
    // This violates the contract: a.equals(b) is true but a.hashCode() != b.hashCode().
}

public class EqualsWithoutHashCode_Broken {
    public static void main(String[] args) {
        User a = new User("Alice", 30);
        User b = new User("Alice", 30);

        System.out.println("a.equals(b)?       " + a.equals(b));        // true
        System.out.println("hashCodes match?   " + (a.hashCode() == b.hashCode())); // usually false

        Set<User> users = new HashSet<>();
        users.add(a);
        System.out.println("\nSet contains a: " + users.contains(a));   // true
        System.out.println("Set contains b: " + users.contains(b));     // FALSE — bug!
        System.out.println("  ^ equals says they're equal, but HashSet looked in the wrong bucket.");
    }
}

// Standard JavaBean-style class. Anyone can call any setter at any time.
// Three problems on display: corrupted state, broken HashSet behavior, thread risk.

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class User {
    private String name;
    private int age;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof User u)) return false;
        return age == u.age && Objects.equals(name, u.name);
    }
    @Override
    public int hashCode() { return Objects.hash(name, age); }
}

public class MutableUserBroken {
    public static void main(String[] args) {
        User alice = new User("Alice", 30);

        Set<User> users = new HashSet<>();
        users.add(alice);
        System.out.println("Set contains alice? " + users.contains(alice));   // true

        // Now mutate the user that's already in the set.
        alice.setName("Bob");
        System.out.println("\nAfter alice.setName(\"Bob\"):");
        System.out.println("Set contains alice? " + users.contains(alice));   // FALSE — bug!
        System.out.println("  ^ alice's hashCode changed, so the set looks in the wrong bucket.");
        System.out.println("  The same instance is in the set, but contains() can't find it.");

        System.out.println("\nMutability of objects used as keys silently breaks hashed collections.");
        System.out.println("This is why immutability matters. See ImmutableUser.java.");
    }
}

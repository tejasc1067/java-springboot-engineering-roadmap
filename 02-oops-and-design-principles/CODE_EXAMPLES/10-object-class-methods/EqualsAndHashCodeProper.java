// equals() and hashCode() done correctly. Both look at the same set of fields.
// Result: two Users with the same data behave as one inside hashed collections.

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class User {
    private final String name;
    private final int age;

    User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return age == other.age && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age);     // same fields as equals — contract upheld
    }

    @Override
    public String toString() {
        return "User{name=" + name + ", age=" + age + "}";
    }
}

public class EqualsAndHashCodeProper {
    public static void main(String[] args) {
        User a = new User("Alice", 30);
        User b = new User("Alice", 30);
        User c = new User("Bob", 25);

        System.out.println("a.equals(b): " + a.equals(b));   // true
        System.out.println("a == b:      " + (a == b));      // false (still different objects)

        Set<User> users = new HashSet<>();
        users.add(a);
        System.out.println("\nSet contains a: " + users.contains(a));   // true
        System.out.println("Set contains b: " + users.contains(b));     // true — equals says so
        System.out.println("Set contains c: " + users.contains(c));     // false — different data

        users.add(b);   // does NOT add — equal to existing element
        System.out.println("Set size after adding b: " + users.size());
    }
}

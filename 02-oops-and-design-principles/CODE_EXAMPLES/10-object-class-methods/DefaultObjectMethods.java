// What `Object` gives you for free — and why it's usually not enough.

class User {
    String name;
    int age;
    User(String name, int age) { this.name = name; this.age = age; }
}

public class DefaultObjectMethods {
    public static void main(String[] args) {
        User a = new User("Alice", 30);
        User b = new User("Alice", 30);
        User c = a;

        // Default toString: ClassName@hash — useless.
        System.out.println("a = " + a);     // User@hexcode
        System.out.println("b = " + b);     // User@differenthex

        // Default equals: same as ==. Only true for the SAME object reference.
        System.out.println("a.equals(b): " + a.equals(b));   // false — different instances
        System.out.println("a.equals(c): " + a.equals(c));   // true  — same reference

        // Default hashCode: derived from identity, not data.
        System.out.println("a.hashCode(): " + a.hashCode());
        System.out.println("b.hashCode(): " + b.hashCode());   // different — even though same data

        System.out.println("\nFor most domain classes you need to override these. See next files.");
    }
}

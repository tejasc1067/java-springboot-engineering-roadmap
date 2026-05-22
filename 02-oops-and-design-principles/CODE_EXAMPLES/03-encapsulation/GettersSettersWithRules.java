// Setters that actually do something. The classic "private field + getter + setter
// that blindly assigns" pattern is encapsulation in name only — no rules are
// enforced. Real encapsulation uses setters to maintain invariants.

class User {
    private String name;
    private int age;

    User(String name, int age) {
        setName(name);
        setAge(age);
    }

    public String getName() { return name; }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("name too long: " + name.length());
        }
        this.name = name;
    }

    public int getAge() { return age; }

    public void setAge(int age) {
        if (age < 0 || age > 150) {
            throw new IllegalArgumentException("age out of range: " + age);
        }
        this.age = age;
    }
}

public class GettersSettersWithRules {
    public static void main(String[] args) {
        User u = new User("Alice", 30);
        System.out.println(u.getName() + ", " + u.getAge());

        u.setAge(31);
        System.out.println(u.getName() + ", " + u.getAge());

        // Each rule below is enforced consistently — whether the value comes
        // through the constructor or a later setter call.
        attempt(() -> u.setName(""));         // blank
        attempt(() -> u.setName(null));       // null
        attempt(() -> u.setAge(-5));          // negative
        attempt(() -> u.setAge(999));         // too high
    }

    private static void attempt(Runnable r) {
        try { r.run(); }
        catch (Exception e) { System.out.println("Rejected: " + e.getMessage()); }
    }
}

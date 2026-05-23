import java.lang.reflect.Constructor;

public class CreateInstanceReflectively {

    static class User {
        private final String name;
        private final int age;
        public User() { this("anonymous", 0); }
        public User(String name, int age) { this.name = name; this.age = age; }
        public String toString() { return "User(" + name + ", " + age + ")"; }
    }

    public static void main(String[] args) throws Exception {
        // No-arg constructor.
        Constructor<User> noArg = User.class.getDeclaredConstructor();
        User u1 = noArg.newInstance();
        System.out.println("no-arg: " + u1);

        // Constructor with parameter types.
        Constructor<User> two = User.class.getDeclaredConstructor(String.class, int.class);
        User u2 = two.newInstance("Alice", 25);
        System.out.println("2-arg:  " + u2);

        System.out.println();
        System.out.println("Frameworks use this exact pattern when wiring beans.");
        System.out.println("(Real frameworks cache the Constructor lookup once at startup.)");
    }
}

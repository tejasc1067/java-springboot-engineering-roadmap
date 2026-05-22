// Without toString, debugging prints look like User@1540e19d.
// A few lines of toString turn the debugger into a useful tool.

class UserDefault {
    String name;
    int age;
    UserDefault(String name, int age) { this.name = name; this.age = age; }
}

class UserWithToString {
    String name;
    int age;
    UserWithToString(String name, int age) { this.name = name; this.age = age; }

    @Override
    public String toString() {
        return "User{name=" + name + ", age=" + age + "}";
    }
}

public class ToStringForDebugging {
    public static void main(String[] args) {
        UserDefault       d = new UserDefault("Alice", 30);
        UserWithToString  w = new UserWithToString("Alice", 30);

        System.out.println("Default toString:   " + d);    // User@xxxxx
        System.out.println("Custom  toString:   " + w);    // User{name=Alice, age=30}

        // Now imagine debugging a List<User>. With the default, every entry
        // is an opaque hex code. With toString, you can read the list.
    }
}

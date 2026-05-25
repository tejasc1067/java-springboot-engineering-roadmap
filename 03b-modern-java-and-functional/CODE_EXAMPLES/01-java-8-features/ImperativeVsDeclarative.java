import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ImperativeVsDeclarative {

    static class User {
        final String name; final int age;
        User(String name, int age) { this.name = name; this.age = age; }
        String getName() { return name; }
        int getAge() { return age; }
    }

    public static void main(String[] args) {
        List<User> users = List.of(
            new User("Alice", 25),
            new User("Bob",   17),
            new User("Carol", 31),
            new User("Dave",  16),
            new User("Eve",   22)
        );

        // 1) Imperative -- describes HOW.
        List<String> imperative = new ArrayList<>();
        for (User u : users) {
            if (u.getAge() >= 18) {
                imperative.add(u.getName());
            }
        }
        Collections.sort(imperative);

        // 2) Declarative -- describes WHAT.
        List<String> declarative = users.stream()
            .filter(u -> u.getAge() >= 18)
            .map(User::getName)
            .sorted()
            .collect(Collectors.toList());

        System.out.println("imperative:  " + imperative);
        System.out.println("declarative: " + declarative);
        System.out.println("match:       " + imperative.equals(declarative));
    }
}

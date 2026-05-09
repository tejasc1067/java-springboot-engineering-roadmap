class User {

    String name;

    User(String name) {

        this.name = name;
    }
}

public class GarbageCollectionExample {

    public static void main(String[] args) {

        User user = new User("Tejas");

        // Object becomes eligible for GC
        user = null;

        System.gc();

        System.out.println(
                "Garbage Collection Requested"
        );
    }
}
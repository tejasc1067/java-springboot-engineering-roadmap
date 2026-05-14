class User {

    String name =
            "Tejas";
}

public class GarbageCollectionBasicExample {

    public static void main(String[] args) {

        User user =
                new User();

        user = null;

        System.gc();

        System.out.println(
                "Object Eligible For Garbage Collection"
        );
    }
}
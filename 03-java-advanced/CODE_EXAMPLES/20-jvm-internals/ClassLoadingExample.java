class User {

    static {

        System.out.println(
                "Class Loaded Into JVM"
        );
    }
}

public class ClassLoadingExample {

    public static void main(String[] args) {

        new User();
    }
}
class User {
}

public class ClassObjectExample {

    public static void main(String[] args)
            throws ClassNotFoundException {

        Class<?> classOne =
                User.class;

        User user =
                new User();

        Class<?> classTwo =
                user.getClass();

        Class<?> classThree =
                Class.forName("User");

        System.out.println(
                classOne.getName()
        );

        System.out.println(
                classTwo.getName()
        );

        System.out.println(
                classThree.getName()
        );
    }
}
import java.util.ArrayList;

public class CollectionUtilityMethodsExample {

    public static void main(String[] args) {

        ArrayList<String> users = new ArrayList<>();

        users.add("Tejas");

        users.add("Rahul");

        users.add("Amit");

        // size()
        System.out.println("Size: " + users.size());

        // contains()
        System.out.println(
                "Contains Rahul? "
                        + users.contains("Rahul")
        );

        // remove()
        users.remove("Amit");

        System.out.println("After Remove: " + users);

        // isEmpty()
        System.out.println(
                "Is Empty? "
                        + users.isEmpty()
        );

        // clear()
        users.clear();

        System.out.println(
                "After Clear: "
                        + users
        );
    }
}
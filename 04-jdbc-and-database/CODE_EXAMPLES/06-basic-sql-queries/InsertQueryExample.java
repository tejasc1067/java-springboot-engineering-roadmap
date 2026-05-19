import java.util.ArrayList;
import java.util.List;

public class InsertQueryExample {

    public static void main(String[] args) {

        List<String> users =
                new ArrayList<>();

        users.add("Tejas");
        users.add("Rahul");

        System.out.println(
                "Users Inserted Successfully"
        );

        System.out.println(users);
    }
}
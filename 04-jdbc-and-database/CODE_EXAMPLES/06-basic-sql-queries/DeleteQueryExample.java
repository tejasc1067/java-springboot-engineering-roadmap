import java.util.ArrayList;
import java.util.List;

public class DeleteQueryExample {

    public static void main(String[] args) {

        List<String> users =
                new ArrayList<>();

        users.add("Tejas");
        users.add("Rahul");

        users.remove("Rahul");

        System.out.println(users);
    }
}
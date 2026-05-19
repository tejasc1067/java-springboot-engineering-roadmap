import java.util.ArrayList;
import java.util.List;

public class UpdateQueryExample {

    public static void main(String[] args) {

        List<String> users =
                new ArrayList<>();

        users.add("Tejas");

        users.set(0, "Tejas Updated");

        System.out.println(users);
    }
}
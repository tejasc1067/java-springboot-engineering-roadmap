import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StreamFilterExample {

    public static void main(String[] args) {

        List<String> users = new ArrayList<>();

        users.add("Tejas");

        users.add("Rahul");

        users.add("Amit");

        users.add("Tushar");

        List<String> filteredUsers =
                users.stream()
                        .filter(user -> user.startsWith("T"))
                        .collect(Collectors.toList());

        System.out.println(filteredUsers);
    }
}
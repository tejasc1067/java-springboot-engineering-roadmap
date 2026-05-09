import java.util.ArrayList;
import java.util.Iterator;

public class ConcurrentModificationExample {

    public static void main(String[] args) {

        ArrayList<String> users = new ArrayList<>();

        users.add("Tejas");

        users.add("Rahul");

        users.add("Amit");

        Iterator<String> iterator = users.iterator();

        while (iterator.hasNext()) {

            String user = iterator.next();

            if (user.equals("Rahul")) {

                // Safe removal using iterator
                iterator.remove();
            }
        }

        System.out.println(users);
    }
}
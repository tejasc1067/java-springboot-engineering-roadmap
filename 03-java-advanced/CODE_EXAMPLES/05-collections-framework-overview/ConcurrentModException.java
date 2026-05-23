import java.util.ArrayList;
import java.util.List;

public class ConcurrentModException {

    public static void main(String[] args) {
        List<String> users = new ArrayList<>(List.of("Alice", "Bob_inactive", "Charlie"));

        try {
            for (String u : users) {
                if (u.endsWith("_inactive")) {
                    users.remove(u);
                }
            }
        } catch (java.util.ConcurrentModificationException e) {
            System.out.println("CME thrown -- mutating the collection during enhanced-for is unsafe.");
            System.out.println("See IteratorRemoveProper.java for the fix.");
        }

        System.out.println("list afterwards: " + users);
    }
}

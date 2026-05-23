import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IterationStyles {

    public static void main(String[] args) {
        List<String> users = new ArrayList<>(List.of("Alice", "Bob", "Charlie"));

        System.out.println("1) index-based:");
        for (int i = 0; i < users.size(); i++) {
            System.out.println("   [" + i + "] " + users.get(i));
        }

        System.out.println("2) enhanced-for:");
        for (String u : users) {
            System.out.println("   - " + u);
        }

        System.out.println("3) explicit Iterator:");
        Iterator<String> it = users.iterator();
        while (it.hasNext()) {
            String u = it.next();
            System.out.println("   - " + u);
        }
    }
}

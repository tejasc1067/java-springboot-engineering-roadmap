import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class IteratorRemoveProper {

    public static void main(String[] args) {
        List<String> usersA = new ArrayList<>(List.of("Alice", "Bob_inactive", "Charlie", "Dana_inactive"));
        List<String> usersB = new ArrayList<>(usersA);

        Iterator<String> it = usersA.iterator();
        while (it.hasNext()) {
            String u = it.next();
            if (u.endsWith("_inactive")) {
                it.remove();
            }
        }
        System.out.println("after iterator.remove(): " + usersA);

        usersB.removeIf(u -> u.endsWith("_inactive"));
        System.out.println("after removeIf():         " + usersB);
    }
}

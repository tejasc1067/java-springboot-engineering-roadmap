// Importing classes from the standard library. The `import` lines let us
// write the short name (ArrayList) instead of the full one (java.util.ArrayList).
//
// Classes from java.lang (String, Math, Integer, ...) need no import — they're
// available everywhere automatically.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;

public class ImportingFromStdlib {
    public static void main(String[] args) {

        ArrayList<String> names = new ArrayList<>();
        names.add("alice");
        names.add("bob");
        System.out.println("names: " + names);

        Map<String, Integer> ages = new HashMap<>();
        ages.put("alice", 30);
        ages.put("bob",   25);
        System.out.println("ages:  " + ages);

        LocalDate today = LocalDate.now();
        System.out.println("today: " + today);

        // You can ALWAYS use the fully-qualified name — the import is a
        // shortcut, not a requirement.
        java.util.ArrayList<String> withFullName = new java.util.ArrayList<>();
        withFullName.add("carol");
        System.out.println("with full name: " + withFullName);
    }
}

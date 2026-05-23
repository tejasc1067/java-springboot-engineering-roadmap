// The for-each loop. Cleaner than the classic for when you don't need the index.

import java.util.List;

public class EnhancedFor {
    public static void main(String[] args) {

        // Over an array
        int[] scores = {90, 75, 60};
        int total = 0;
        for (int s : scores) {
            total += s;
        }
        System.out.println("array sum = " + total);

        // Over a list
        List<String> names = List.of("alice", "bob", "carol");
        for (String name : names) {
            System.out.println("  hello, " + name);
        }

        // Limitation: you can't get the index this way. If you need it, use
        // the classic for, or track it manually:
        int i = 0;
        for (String name : names) {
            System.out.println("  [" + i + "] " + name);
            i++;
        }
    }
}

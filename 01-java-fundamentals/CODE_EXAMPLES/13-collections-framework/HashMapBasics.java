// HashMap: key->value lookup, average O(1). The most useful collection in Java.

import java.util.HashMap;
import java.util.Map;

public class HashMapBasics {
    public static void main(String[] args) {

        Map<String, Integer> ages = new HashMap<>();
        ages.put("alice", 30);
        ages.put("bob", 25);
        ages.put("carol", 28);

        // Read
        System.out.println("alice's age: " + ages.get("alice"));

        // Missing key returns null
        System.out.println("dave's age:  " + ages.get("dave"));      // null

        // getOrDefault avoids the NPE downstream
        int daveAge = ages.getOrDefault("dave", -1);
        System.out.println("dave (default): " + daveAge);

        // Update
        ages.put("alice", 31);                  // overwrites
        System.out.println("alice updated: " + ages.get("alice"));

        // Existence
        System.out.println("contains carol: " + ages.containsKey("carol"));

        // Remove
        ages.remove("bob");

        // Iterate — entrySet gives you key + value
        System.out.println("\nall entries:");
        for (Map.Entry<String, Integer> entry : ages.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }

        // Lambda form
        System.out.println("\nvia forEach:");
        ages.forEach((name, age) -> System.out.println("  " + name + " is " + age));

        // Word frequency counter — a real-world HashMap pattern.
        String[] words = {"a", "b", "a", "c", "b", "a"};
        Map<String, Integer> freq = new HashMap<>();
        for (String w : words) {
            freq.put(w, freq.getOrDefault(w, 0) + 1);
        }
        System.out.println("\nfrequencies: " + freq);
    }
}

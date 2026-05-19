import java.util.TreeMap;

public class ClusteredIndexSimulation {

    public static void main(String[] args) {

        TreeMap<Integer, String> users =
                new TreeMap<>();

        users.put(3, "Amit");
        users.put(1, "Tejas");
        users.put(2, "Rahul");

        System.out.println(users);
    }
}
import java.util.TreeMap;

public class TreeMapRangeQueries {

    public static void main(String[] args) {
        TreeMap<Integer, String> events = new TreeMap<>();
        events.put(100, "login");
        events.put(105, "view-product");
        events.put(110, "add-to-cart");
        events.put(150, "checkout");
        events.put(200, "logout");

        System.out.println("all events (sorted by timestamp):");
        events.forEach((k, v) -> System.out.println("  " + k + " -> " + v));

        System.out.println();
        System.out.println("events between t=105 and t=199 (inclusive..exclusive):");
        events.subMap(105, 200).forEach((k, v) -> System.out.println("  " + k + " -> " + v));

        System.out.println();
        System.out.println("first event:                " + events.firstEntry());
        System.out.println("last event:                 " + events.lastEntry());
        System.out.println("event at or before t=130:   " + events.floorEntry(130));
        System.out.println("event at or after t=130:    " + events.ceilingEntry(130));
    }
}

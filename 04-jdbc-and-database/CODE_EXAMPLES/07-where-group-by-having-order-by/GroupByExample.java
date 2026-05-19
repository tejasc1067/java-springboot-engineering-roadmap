import java.util.HashMap;
import java.util.Map;

public class GroupByExample {

    public static void main(String[] args) {

        Map<String, Integer> departments =
                new HashMap<>();

        departments.put("Engineering", 5);
        departments.put("HR", 2);

        for (Map.Entry<String, Integer> entry
                : departments.entrySet()) {

            System.out.println(
                    entry.getKey()
                            + " -> "
                            + entry.getValue()
            );
        }
    }
}
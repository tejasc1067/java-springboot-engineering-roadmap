import java.util.HashMap;
import java.util.Map;

public class HavingClauseExample {

    public static void main(String[] args) {

        Map<String, Integer> departments =
                new HashMap<>();

        departments.put("Engineering", 10);
        departments.put("HR", 2);

        for (Map.Entry<String, Integer> entry
                : departments.entrySet()) {

            if (entry.getValue() > 5) {

                System.out.println(
                        entry.getKey()
                );
            }
        }
    }
}
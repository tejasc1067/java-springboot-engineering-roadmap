import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FailFastVsFailSafeExample {

    public static void main(String[] args) {

        // Fail-Safe Example

        CopyOnWriteArrayList<String> safeList =
                new CopyOnWriteArrayList<>();

        safeList.add("Java");

        safeList.add("Spring");

        for (String value : safeList) {

            safeList.add("AWS");

            System.out.println(value);
        }

        // Fail-Fast Example

        List<String> failFastList =
                new ArrayList<>();

        failFastList.add("A");

        failFastList.add("B");

        try {

            for (String value : failFastList) {

                failFastList.add("C");
            }

        } catch (Exception exception) {

            System.out.println(
                    "Concurrent Modification Detected"
            );
        }
    }
}
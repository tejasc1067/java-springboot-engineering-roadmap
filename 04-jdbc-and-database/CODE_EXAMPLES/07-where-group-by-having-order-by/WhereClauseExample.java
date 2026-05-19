import java.util.Arrays;
import java.util.List;

public class WhereClauseExample {

    public static void main(String[] args) {

        List<Integer> userIds =
                Arrays.asList(1, 2, 3, 4);

        for (Integer id : userIds) {

            if (id == 2) {

                System.out.println(
                        "Filtered User ID: " + id
                );
            }
        }
    }
}
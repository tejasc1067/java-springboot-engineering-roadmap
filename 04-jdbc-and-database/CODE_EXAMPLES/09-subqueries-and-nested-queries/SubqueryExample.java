import java.util.Arrays;
import java.util.List;

public class SubqueryExample {

    public static void main(String[] args) {

        List<Integer> orderUserIds =
                Arrays.asList(1, 2);

        List<Integer> users =
                Arrays.asList(1, 2, 3);

        for (Integer userId : users) {

            if (orderUserIds.contains(userId)) {

                System.out.println(
                        "User With Orders: "
                                + userId
                );
            }
        }
    }
}
import java.util.Arrays;
import java.util.List;

public class ExistsOperatorExample {

    public static void main(String[] args) {

        List<Integer> orders =
                Arrays.asList(101, 102);

        if (!orders.isEmpty()) {

            System.out.println(
                    "Orders Exist"
            );
        }
    }
}
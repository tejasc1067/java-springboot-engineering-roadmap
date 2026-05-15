import java.util.List;

public class ReduceExample {

    public static void main(String[] args) {

        List<Integer> numbers =
                List.of(10, 20, 30, 40);

        int total =
                numbers.stream()
                        .reduce(
                                0,
                                Integer::sum
                        );

        System.out.println(
                "Total: " + total
        );
    }
}
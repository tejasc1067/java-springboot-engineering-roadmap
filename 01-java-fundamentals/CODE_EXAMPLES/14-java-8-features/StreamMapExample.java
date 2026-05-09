import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StreamMapExample {

    public static void main(String[] args) {

        List<Integer> numbers =
                Arrays.asList(1, 2, 3, 4, 5);

        List<Integer> squaredNumbers =
                numbers.stream()
                        .map(number -> number * number)
                        .collect(Collectors.toList());

        System.out.println(squaredNumbers);
    }
}
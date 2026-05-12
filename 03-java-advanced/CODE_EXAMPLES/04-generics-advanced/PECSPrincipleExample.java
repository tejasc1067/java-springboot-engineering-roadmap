import java.util.Arrays;
import java.util.List;

public class PECSPrincipleExample {

    static void readNumbers(
            List<? extends Number> numbers
    ) {

        for (Number number : numbers) {

            System.out.println(number);
        }
    }

    static void writeNumbers(
            List<? super Integer> numbers
    ) {

        numbers.add(100);

        numbers.add(200);
    }

    public static void main(String[] args) {

        List<Integer> integers =
                Arrays.asList(1, 2, 3);

        readNumbers(integers);
    }
}
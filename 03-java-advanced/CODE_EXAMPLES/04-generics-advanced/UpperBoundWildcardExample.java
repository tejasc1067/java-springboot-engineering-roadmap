import java.util.Arrays;
import java.util.List;

public class UpperBoundWildcardExample {

    static void printNumbers(
            List<? extends Number> numbers
    ) {

        for (Number number : numbers) {

            System.out.println(number);
        }
    }

    public static void main(String[] args) {

        List<Integer> integers =
                Arrays.asList(1, 2, 3);

        List<Double> doubles =
                Arrays.asList(10.5, 20.5);

        printNumbers(integers);

        printNumbers(doubles);
    }
}
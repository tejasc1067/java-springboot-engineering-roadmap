import java.util.ArrayList;
import java.util.List;

public class LowerBoundWildcardExample {

    static void addNumbers(
            List<? super Integer> numbers
    ) {

        numbers.add(10);

        numbers.add(20);
    }

    public static void main(String[] args) {

        List<Number> numbers =
                new ArrayList<>();

        addNumbers(numbers);

        System.out.println(numbers);
    }
}
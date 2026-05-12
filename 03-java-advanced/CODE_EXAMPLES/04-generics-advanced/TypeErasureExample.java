import java.util.ArrayList;
import java.util.List;

public class TypeErasureExample {

    public static void main(String[] args) {

        List<String> names =
                new ArrayList<>();

        List<Integer> numbers =
                new ArrayList<>();

        System.out.println(
                names.getClass()
                        == numbers.getClass()
        );
    }
}
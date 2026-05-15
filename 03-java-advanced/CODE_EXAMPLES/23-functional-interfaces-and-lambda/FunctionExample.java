import java.util.function.Function;

public class FunctionExample {

    public static void main(String[] args) {

        Function<String, String> toUpperCase =
                name -> name.toUpperCase();

        String result =
                toUpperCase.apply("tejas");

        System.out.println(result);
    }
}
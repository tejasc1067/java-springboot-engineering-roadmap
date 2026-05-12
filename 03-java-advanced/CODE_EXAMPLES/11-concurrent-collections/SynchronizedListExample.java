import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SynchronizedListExample {

    public static void main(String[] args) {

        List<String> services =
                Collections.synchronizedList(
                        new ArrayList<>()
                );

        services.add("User Service");

        services.add("Payment Service");

        System.out.println(services);
    }
}
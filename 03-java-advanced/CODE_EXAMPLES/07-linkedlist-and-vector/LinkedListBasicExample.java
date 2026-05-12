import java.util.LinkedList;
import java.util.List;

public class LinkedListBasicExample {

    public static void main(String[] args) {

        List<String> services =
                new LinkedList<>();

        services.add("User Service");

        services.add("Payment Service");

        services.add("Order Service");

        System.out.println(services);
    }
}
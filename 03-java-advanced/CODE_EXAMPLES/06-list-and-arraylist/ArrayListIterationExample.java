import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ArrayListIterationExample {

    public static void main(String[] args) {

        List<String> services =
                new ArrayList<>();

        services.add("User Service");

        services.add("Payment Service");

        services.add("Order Service");

        // For Loop

        for (int index = 0;
             index < services.size();
             index++) {

            System.out.println(
                    services.get(index)
            );
        }

        // Enhanced For Loop

        for (String service : services) {

            System.out.println(service);
        }

        // Iterator

        Iterator<String> iterator =
                services.iterator();

        while (iterator.hasNext()) {

            System.out.println(
                    iterator.next()
            );
        }
    }
}
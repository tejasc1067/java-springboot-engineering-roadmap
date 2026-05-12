import java.util.LinkedList;
import java.util.Queue;

public class QueueExample {

    public static void main(String[] args) {

        Queue<String> requests =
                new LinkedList<>();

        requests.add("Request-1");

        requests.add("Request-2");

        requests.add("Request-3");

        System.out.println(
                requests.poll()
        );

        System.out.println(requests);
    }
}
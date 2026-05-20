import java.util.ArrayDeque;
import java.util.Queue;

public class BasicConnectionPoolingSimulation {

    private static final Queue<String> connectionPool =
            new ArrayDeque<>();

    static {

        connectionPool.add("Connection-1");
        connectionPool.add("Connection-2");
        connectionPool.add("Connection-3");
    }

    public static void main(String[] args) {

        String connection =
                connectionPool.poll();

        System.out.println(
                "Using: " + connection
        );

        connectionPool.offer(connection);

        System.out.println(
                "Returned Connection To Pool"
        );
    }
}
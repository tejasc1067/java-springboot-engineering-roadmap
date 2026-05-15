import java.util.ArrayList;
import java.util.List;

class Connection {

    private final int id;

    public Connection(int id) {

        this.id = id;
    }

    @Override
    public String toString() {

        return "Connection-" + id;
    }
}

public class ConnectionPoolSimulationExample {

    public static void main(String[] args) {

        List<Connection> pool =
                new ArrayList<>();

        for (int i = 1; i <= 3; i++) {

            pool.add(new Connection(i));
        }

        Connection connection =
                pool.get(0);

        System.out.println(
                "Using: " + connection
        );
    }
}
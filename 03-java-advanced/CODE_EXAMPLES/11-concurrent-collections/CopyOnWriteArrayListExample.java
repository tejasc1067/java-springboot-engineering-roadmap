import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteArrayListExample {

    public static void main(String[] args) {

        CopyOnWriteArrayList<String> logs =
                new CopyOnWriteArrayList<>();

        logs.add("INFO");

        logs.add("WARN");

        for (String log : logs) {

            System.out.println(log);

            logs.add("NEW_LOG");
        }

        System.out.println(logs);
    }
}
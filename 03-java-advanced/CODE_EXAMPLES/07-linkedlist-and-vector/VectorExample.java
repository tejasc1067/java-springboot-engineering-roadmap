import java.util.Vector;

public class VectorExample {

    public static void main(String[] args) {

        Vector<String> logs =
                new Vector<>();

        logs.add("INFO");

        logs.add("WARN");

        logs.add("ERROR");

        System.out.println(logs);
    }
}
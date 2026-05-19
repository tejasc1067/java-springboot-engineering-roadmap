public class CreateTableSimulation {

    public static void main(String[] args) {

        String createTableQuery =
                "CREATE TABLE users (" +
                        "id INT, " +
                        "name VARCHAR(100))";

        System.out.println(
                createTableQuery
        );
    }
}
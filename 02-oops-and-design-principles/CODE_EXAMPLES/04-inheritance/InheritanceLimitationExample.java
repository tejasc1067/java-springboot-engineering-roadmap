class DatabaseConnection {

    void connect() {

        System.out.println(
                "Database Connected"
        );
    }
}

class MySQLConnection extends DatabaseConnection {

    void mysqlSpecificFeature() {

        System.out.println(
                "MySQL Specific Logic"
        );
    }
}

public class InheritanceLimitationExample {

    public static void main(String[] args) {

        MySQLConnection connection =
                new MySQLConnection();

        connection.connect();

        connection.mysqlSpecificFeature();

        System.out.println();

        System.out.println(
                "Excessive inheritance may create tight coupling."
        );

        System.out.println(
                "Modern backend systems often prefer composition."
        );
    }
}
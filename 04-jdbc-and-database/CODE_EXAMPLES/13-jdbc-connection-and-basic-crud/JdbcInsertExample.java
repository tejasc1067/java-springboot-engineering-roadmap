public class JdbcInsertExample {

    public static void main(String[] args) {

        String insertQuery =
                "INSERT INTO users(name,email) " +
                        "VALUES('Tejas','tejas@email.com')";

        System.out.println(
                "Executing Insert Query"
        );

        System.out.println(insertQuery);

        System.out.println(
                "User Inserted Successfully"
        );
    }
}
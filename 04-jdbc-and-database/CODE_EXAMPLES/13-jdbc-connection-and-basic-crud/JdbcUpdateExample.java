public class JdbcUpdateExample {

    public static void main(String[] args) {

        String updateQuery =
                "UPDATE users " +
                        "SET name='Updated User' " +
                        "WHERE id=1";

        System.out.println(
                "Executing Update Query"
        );

        System.out.println(updateQuery);

        System.out.println(
                "User Updated Successfully"
        );
    }
}
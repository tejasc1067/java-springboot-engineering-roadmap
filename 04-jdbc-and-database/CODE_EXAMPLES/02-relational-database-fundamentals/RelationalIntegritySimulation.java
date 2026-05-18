public class RelationalIntegritySimulation {

    public static void main(String[] args) {

        int existingUserId = 1;

        int orderUserId = 1;

        if (existingUserId == orderUserId) {

            System.out.println(
                    "Valid Relationship"
            );

        } else {

            System.out.println(
                    "Invalid Relationship"
            );
        }
    }
}
public class RollbackWorkflowJdbcExample {

    public static void main(String[] args) {

        System.out.println(
                "Execute Payment Deduction"
        );

        System.out.println(
                "Inventory Update Failed"
        );

        System.out.println(
                "Rollback JDBC Transaction"
        );

        System.out.println(
                "Database State Restored"
        );
    }
}
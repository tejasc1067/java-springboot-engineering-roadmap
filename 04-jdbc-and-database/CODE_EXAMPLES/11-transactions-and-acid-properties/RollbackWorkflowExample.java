public class RollbackWorkflowExample {

    public static void main(String[] args) {

        System.out.println(
                "Begin Transaction"
        );

        System.out.println(
                "Payment Deducted"
        );

        System.out.println(
                "Inventory Update Failed"
        );

        System.out.println(
                "ROLLBACK Transaction"
        );
    }
}
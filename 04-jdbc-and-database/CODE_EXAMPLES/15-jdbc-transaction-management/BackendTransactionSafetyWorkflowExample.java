public class BackendTransactionSafetyWorkflowExample {

    public static void main(String[] args) {

        System.out.println(
                "Receive Checkout Request"
        );

        System.out.println(
                "Disable Auto Commit"
        );

        System.out.println(
                "Execute Multiple Queries"
        );

        System.out.println(
                "Commit If Success"
        );

        System.out.println(
                "Rollback If Failure"
        );

        System.out.println(
                "Return Consistent API Response"
        );
    }
}
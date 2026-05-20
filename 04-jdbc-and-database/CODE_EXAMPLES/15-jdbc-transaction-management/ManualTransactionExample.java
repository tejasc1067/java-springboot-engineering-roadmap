public class ManualTransactionExample {

    public static void main(String[] args) {

        boolean autoCommit =
                false;

        System.out.println(
                "Auto Commit Disabled"
        );

        System.out.println(
                "Manual Transaction Control Enabled"
        );

        System.out.println(
                "Commit or Rollback Required"
        );
    }
}
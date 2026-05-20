public class AutoCommitExample {

    public static void main(String[] args) {

        boolean autoCommit =
                true;

        System.out.println(
                "Auto Commit Mode: "
                        + autoCommit
        );

        System.out.println(
                "Each Query Commits Automatically"
        );
    }
}
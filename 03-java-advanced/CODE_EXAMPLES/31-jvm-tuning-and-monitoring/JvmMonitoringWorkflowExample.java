public class JvmMonitoringWorkflowExample {

    public static void main(String[] args)
            throws Exception {

        System.out.println(
                "JVM Monitoring Started"
        );

        while (true) {

            long memory =
                    Runtime.getRuntime()
                            .freeMemory();

            System.out.println(
                    "Free Memory: "
                            + memory
            );

            Thread.sleep(3000);
        }
    }
}
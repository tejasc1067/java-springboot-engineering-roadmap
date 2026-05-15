public class ProductionJvmWorkflowExample {

    public static void main(String[] args)
            throws Exception {

        Runtime runtime =
                Runtime.getRuntime();

        while (true) {

            long usedMemory =
                    runtime.totalMemory()
                            - runtime.freeMemory();

            System.out.println(
                    "Used Memory: "
                            + usedMemory
            );

            System.out.println(
                    "Available Processors: "
                            + runtime.availableProcessors()
            );

            Thread.sleep(5000);
        }
    }
}
public class BackendJvmMonitoringExample {

    public static void main(String[] args) {

        Runtime runtime =
                Runtime.getRuntime();

        long totalMemory =
                runtime.totalMemory();

        long freeMemory =
                runtime.freeMemory();

        long usedMemory =
                totalMemory - freeMemory;

        System.out.println(
                "Total Memory: "
                        + totalMemory
        );

        System.out.println(
                "Used Memory: "
                        + usedMemory
        );

        System.out.println(
                "Free Memory: "
                        + freeMemory
        );
    }
}
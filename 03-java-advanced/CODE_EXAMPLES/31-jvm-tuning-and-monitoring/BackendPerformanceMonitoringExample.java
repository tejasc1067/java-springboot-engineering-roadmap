public class BackendPerformanceMonitoringExample {

    public static void main(String[] args) {

        long startTime =
                System.currentTimeMillis();

        for (int i = 0; i < 1000000; i++) {

            String value =
                    "Backend Monitoring";
        }

        long endTime =
                System.currentTimeMillis();

        System.out.println(
                "Execution Time: "
                        + (endTime - startTime)
                        + " ms"
        );
    }
}
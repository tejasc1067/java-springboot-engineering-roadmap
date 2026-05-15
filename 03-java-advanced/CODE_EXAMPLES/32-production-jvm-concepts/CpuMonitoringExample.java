import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public class CpuMonitoringExample {

    public static void main(String[] args) {

        OperatingSystemMXBean osBean =
                ManagementFactory.getOperatingSystemMXBean();

        System.out.println(
                "Available Processors: "
                        + osBean.getAvailableProcessors()
        );

        System.out.println(
                "System Load Average: "
                        + osBean.getSystemLoadAverage()
        );
    }
}
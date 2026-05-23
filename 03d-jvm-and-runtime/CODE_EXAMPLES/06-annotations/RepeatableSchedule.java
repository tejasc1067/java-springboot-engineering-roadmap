import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

public class RepeatableSchedule {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Repeatable(Schedules.class)               // points at the container annotation
    @interface Schedule { String cron(); }

    // The implicit container the compiler builds when @Schedule appears multiple times.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Schedules { Schedule[] value(); }

    static class Jobs {
        @Schedule(cron = "0 * * * *")
        @Schedule(cron = "30 * * * *")
        public void reportEveryHalfHour() {}
    }

    public static void main(String[] args) throws Exception {
        Method m = Jobs.class.getDeclaredMethod("reportEveryHalfHour");

        // Flat form (Java 8+) -- yields all individual @Schedule instances.
        System.out.println("flat (getAnnotationsByType):");
        for (Schedule s : m.getAnnotationsByType(Schedule.class)) {
            System.out.println("  cron = " + s.cron());
        }

        // Container form -- the synthesized @Schedules wrapper.
        System.out.println("container (getAnnotation(Schedules.class)):");
        Schedules wrapper = m.getAnnotation(Schedules.class);
        for (Schedule s : wrapper.value()) {
            System.out.println("  cron = " + s.cron());
        }
    }
}

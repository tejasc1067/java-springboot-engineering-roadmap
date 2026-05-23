import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

public class AnnotationDrivenInvocation {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Audit { String action(); }

    static class OrderService {
        @Audit(action = "place-order")
        public void placeOrder(String item) {
            System.out.println("  -> placing order for " + item);
        }
        @Audit(action = "cancel-order")
        public void cancelOrder(String id) {
            System.out.println("  -> cancelling " + id);
        }
        public void internalHousekeeping() { System.out.println("  -> housekeeping"); }
    }

    public static void main(String[] args) throws Exception {
        OrderService svc = new OrderService();

        // Imagine each call below comes from a request handler. The "framework"
        // wraps annotated methods in audit-logging without changing their bodies.
        callViaAudit(svc, "placeOrder",          new Class<?>[] { String.class }, new Object[] { "laptop" });
        callViaAudit(svc, "cancelOrder",         new Class<?>[] { String.class }, new Object[] { "ord-42" });
        callViaAudit(svc, "internalHousekeeping", new Class<?>[] {},               new Object[] {});
    }

    static void callViaAudit(Object target, String name, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, paramTypes);
        Audit a = m.getAnnotation(Audit.class);
        if (a != null) {
            System.out.println("[before] audit action=" + a.action());
            m.invoke(target, args);
            System.out.println("[after ] audit action=" + a.action());
        } else {
            m.invoke(target, args);                // no annotation -- no wrapping
        }
    }
}

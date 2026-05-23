import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

public class CustomAnnotationDeclare {

    // Declare an annotation. RUNTIME so reflection can see it. METHOD-only target.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Audit {
        String action();                       // required attribute
        int priority() default 5;              // optional, default 5
    }

    static class UserService {
        @Audit(action = "delete-user", priority = 1)
        public void deleteUser(long id) { System.out.println("deleting user " + id); }

        @Audit(action = "read-user")
        public void readUser(long id) { System.out.println("reading user " + id); }

        public void notAudited() { System.out.println("not audited"); }
    }

    public static void main(String[] args) {
        for (Method m : UserService.class.getDeclaredMethods()) {
            Audit a = m.getAnnotation(Audit.class);
            if (a == null) {
                System.out.println("(skip)   " + m.getName());
            } else {
                System.out.println("[AUDIT]  " + m.getName() + " action=" + a.action() + " priority=" + a.priority());
            }
        }
    }
}

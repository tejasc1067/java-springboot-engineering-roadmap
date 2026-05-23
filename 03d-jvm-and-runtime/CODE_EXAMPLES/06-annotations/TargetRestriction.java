import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

public class TargetRestriction {

    // This annotation can ONLY be placed on a method.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface MethodOnly {}

    // Legal placement.
    @MethodOnly
    static void okMethod() {}

    // Trying to place @MethodOnly on a class would fail at compile time:
    //     "@MethodOnly not applicable to type"
    // So we can't even SHOW the broken code here without breaking the build.
    // The compiler enforced the @Target restriction for us.

    public static void main(String[] args) throws Exception {
        Method m = TargetRestriction.class.getDeclaredMethod("okMethod");
        System.out.println("okMethod has @MethodOnly?  " + m.isAnnotationPresent(MethodOnly.class));
        System.out.println();
        System.out.println("@Target turns mistakes into compile errors instead of silent no-ops.");
        System.out.println("Try adding @MethodOnly to a CLASS in your editor and watch the compile fail.");
    }
}

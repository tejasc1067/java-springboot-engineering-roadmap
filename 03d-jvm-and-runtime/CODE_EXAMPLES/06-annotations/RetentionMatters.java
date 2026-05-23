import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

public class RetentionMatters {

    // RUNTIME -- reflection will find this annotation.
    @Retention(RetentionPolicy.RUNTIME)
    @interface VisibleAtRuntime {}

    // CLASS (the default if @Retention is omitted) -- in the .class file, but invisible to reflection.
    @Retention(RetentionPolicy.CLASS)
    @interface OnlyInClassFile {}

    @VisibleAtRuntime
    @OnlyInClassFile
    static void target() {}

    public static void main(String[] args) throws Exception {
        Method m = RetentionMatters.class.getDeclaredMethod("target");

        System.out.println("VisibleAtRuntime present? " + m.isAnnotationPresent(VisibleAtRuntime.class));
        System.out.println("OnlyInClassFile  present? " + m.isAnnotationPresent(OnlyInClassFile.class));
        System.out.println();
        System.out.println("Both annotations are on the method in source. Only RUNTIME retention is visible via reflection.");
        System.out.println("This is the #1 'why doesn't my framework see my annotation' bug.");
    }
}

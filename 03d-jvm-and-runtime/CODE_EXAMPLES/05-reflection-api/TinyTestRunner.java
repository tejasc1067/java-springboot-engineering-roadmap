import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TinyTestRunner {

    // Annotation must be retained at RUNTIME for reflection to see it.
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Test {}

    // The "tests" we'll discover. Same shape as JUnit.
    static class CalculatorTests {
        @Test public void addingWorks() {
            if (1 + 1 != 2) throw new AssertionError("math is broken");
        }
        @Test public void subtractingWorks() {
            if (5 - 3 != 2) throw new AssertionError("subtraction is broken");
        }
        @Test public void deliberatelyFails() {
            throw new AssertionError("this one is meant to fail");
        }
        // Not annotated -- not picked up.
        public void notATest() { System.out.println("would NOT be invoked"); }
    }

    public static void main(String[] args) throws Exception {
        Class<?> testClass = CalculatorTests.class;
        Object instance = testClass.getDeclaredConstructor().newInstance();

        int passed = 0, failed = 0;
        for (Method m : testClass.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(Test.class)) continue;
            try {
                m.invoke(instance);
                System.out.println("[PASS] " + m.getName());
                passed++;
            } catch (InvocationTargetException e) {
                System.out.println("[FAIL] " + m.getName() + " -- " + e.getCause().getMessage());
                failed++;
            }
        }
        System.out.println();
        System.out.println("passed=" + passed + ", failed=" + failed);
    }
}

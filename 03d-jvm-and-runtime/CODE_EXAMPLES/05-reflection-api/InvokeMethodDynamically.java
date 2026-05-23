import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InvokeMethodDynamically {

    static class Calculator {
        public int add(int a, int b) { return a + b; }
        public static String hello() { return "hello, world"; }
        public int divide(int a, int b) { return a / b; }
    }

    public static void main(String[] args) throws Exception {
        // 1) Instance method
        Method add = Calculator.class.getDeclaredMethod("add", int.class, int.class);
        Object result = add.invoke(new Calculator(), 3, 4);
        System.out.println("add(3, 4) = " + result + "  (returned as Object, cast to use)");

        // 2) Static method -- pass null as the target.
        Method hello = Calculator.class.getDeclaredMethod("hello");
        System.out.println("hello() = " + hello.invoke(null));

        // 3) Method that throws -- the exception is wrapped in InvocationTargetException.
        Method divide = Calculator.class.getDeclaredMethod("divide", int.class, int.class);
        try {
            divide.invoke(new Calculator(), 1, 0);
        } catch (InvocationTargetException e) {
            System.out.println("divide threw: wrapper=" + e.getClass().getSimpleName()
                + ", cause=" + e.getCause().getClass().getSimpleName());
        }
    }
}

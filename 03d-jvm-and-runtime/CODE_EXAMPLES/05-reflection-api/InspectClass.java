import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class InspectClass {

    static class Customer {
        private final String name;
        private int age;
        public Customer(String name, int age) { this.name = name; this.age = age; }
        public String getName() { return name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        private void log() { System.out.println("customer " + name); }
    }

    public static void main(String[] args) {
        Class<?> c = Customer.class;
        System.out.println("class:    " + c.getName());
        System.out.println("modifier: " + Modifier.toString(c.getModifiers()));
        System.out.println("parent:   " + c.getSuperclass().getName());

        System.out.println();
        System.out.println("=== declared fields ===");
        for (Field f : c.getDeclaredFields()) {
            System.out.println("  " + Modifier.toString(f.getModifiers()) + " " + f.getType().getSimpleName() + " " + f.getName());
        }

        System.out.println();
        System.out.println("=== declared methods ===");
        for (Method m : c.getDeclaredMethods()) {
            System.out.println("  " + Modifier.toString(m.getModifiers()) + " " + m.getReturnType().getSimpleName() + " " + m.getName());
        }

        System.out.println();
        System.out.println("=== declared constructors ===");
        for (Constructor<?> ctor : c.getDeclaredConstructors()) {
            System.out.println("  " + ctor);
        }
    }
}

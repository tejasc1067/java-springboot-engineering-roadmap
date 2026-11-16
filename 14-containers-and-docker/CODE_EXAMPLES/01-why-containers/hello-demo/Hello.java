// A tiny program compiled with Java 21. It uses a Java 21 feature (pattern matching for switch),
// but even without one, a class compiled by javac 21 records "class file version 65" and refuses
// to load on an older JVM.
public class Hello {
    sealed interface Shape permits Circle, Square { }
    record Circle(double r) implements Shape { }
    record Square(double side) implements Shape { }

    static double area(Shape s) {
        return switch (s) {
            case Circle c -> Math.PI * c.r() * c.r();
            case Square q -> q.side() * q.side();
        };
    }

    public static void main(String[] args) {
        System.out.printf("Hello from Java %s on %s (%s)%n",
                System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch"));
        System.out.printf("Square area: %.1f%n", area(new Square(3)));
    }
}

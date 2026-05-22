// final on local variables, parameters, and instance fields.

class Circle {
    private final double radius;     // instance field — set once in constructor

    Circle(double radius) {
        this.radius = radius;
    }

    double area() {
        final double pi = 3.14159;   // local variable — assigned once
        return pi * radius * radius;
    }

    // Final parameter — caller's value can't be reassigned inside the method.
    // Mostly a documentation hint.
    double scale(final double factor) {
        // factor = factor * 2;    // ← would not compile
        return area() * factor;
    }
}

public class FinalVariable {
    public static void main(String[] args) {
        Circle c = new Circle(5);
        System.out.println("area = " + c.area());
        System.out.println("scaled = " + c.scale(2.0));

        // c.radius = 10;   // ← would not compile: radius is final
    }
}

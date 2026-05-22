// Shape is abstract — it can't be instantiated directly. Subclasses must
// implement the abstract methods.

abstract class Shape {
    String color;

    Shape(String color) {
        this.color = color;
    }

    // Subclass must implement these.
    abstract double area();
    abstract String shapeName();

    // Concrete method that uses the abstract ones.
    void describe() {
        System.out.printf("A %s %s, area = %.2f%n", color, shapeName(), area());
    }
}

class Circle extends Shape {
    double radius;
    Circle(String color, double radius) {
        super(color);
        this.radius = radius;
    }
    @Override double area()      { return Math.PI * radius * radius; }
    @Override String shapeName() { return "circle"; }
}

class Rectangle extends Shape {
    double width, height;
    Rectangle(String color, double width, double height) {
        super(color);
        this.width = width;
        this.height = height;
    }
    @Override double area()      { return width * height; }
    @Override String shapeName() { return "rectangle"; }
}

public class BasicAbstractClass {
    public static void main(String[] args) {
        Shape[] shapes = {
                new Circle("red", 5),
                new Rectangle("blue", 4, 6)
        };
        for (Shape s : shapes) {
            s.describe();
        }
    }
}

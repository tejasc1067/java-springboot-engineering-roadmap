// The same idea ("a Shape has an area") expressed both ways.
// Read both. Notice what each form forces you to do.

// ---- Version A: interface ----
interface ShapeInterface {
    double area();
    default String describe() {                 // shared via default method
        return "a shape with area " + area();
    }
}

class CircleI implements ShapeInterface {
    private final double r;
    CircleI(double r) { this.r = r; }
    public double area() { return Math.PI * r * r; }
}

// ---- Version B: abstract class ----
abstract class ShapeAbstract {
    private final String color;                  // shared state — only possible here
    ShapeAbstract(String color) { this.color = color; }
    abstract double area();
    String describe() {
        return "a " + color + " shape with area " + area();
    }
}

class CircleA extends ShapeAbstract {
    private final double r;
    CircleA(String color, double r) {
        super(color);
        this.r = r;
    }
    @Override double area() { return Math.PI * r * r; }
}

public class InterfaceVsAbstractClass {
    public static void main(String[] args) {
        System.out.println(new CircleI(5).describe());                // interface form
        System.out.println(new CircleA("red", 5).describe());         // abstract class form

        // Which to choose?
        //   - Need shared per-instance state (fields)?   → abstract class
        //   - Multiple unrelated implementations might share the same capability? → interface
        //   - Both?  Often the answer is: an interface + an abstract base class
        //     that implements it (e.g. List + AbstractList in the JDK).
    }
}

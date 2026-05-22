// Trying to instantiate an abstract class. Won't compile.
// Uncomment the marked line to see the error.

abstract class Shape {
    abstract double area();
}

public class CannotInstantiate_Broken {
    public static void main(String[] args) {
        // Shape s = new Shape();   // ← compile error: "Shape is abstract; cannot be instantiated"

        // You can only create instances of CONCRETE subclasses.
        // Uncomment Shape s above; the compiler is unambiguous about why it fails.

        System.out.println("Abstract classes exist to be extended, not instantiated directly.");
    }
}

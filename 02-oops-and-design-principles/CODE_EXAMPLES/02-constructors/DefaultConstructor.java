// When you don't write any constructor, Java supplies a no-arg one for free.
// Every field gets its default value: null for refs, 0 for numbers, false for bools.

class Car {
    String brand;
    int year;
    boolean electric;
    // No constructor defined.
}

public class DefaultConstructor {
    public static void main(String[] args) {
        Car c = new Car();    // uses the implicit default constructor
        System.out.println("brand:    " + c.brand);      // null
        System.out.println("year:     " + c.year);       // 0
        System.out.println("electric: " + c.electric);   // false

        // We have to set every field by hand. If we forget one, the object
        // is half-built — exactly what user-written constructors prevent.
        c.brand = "Toyota";
        c.year = 2018;
        c.electric = true;
        System.out.println("\nAfter manual setup: " + c.year + " " + c.brand + " (electric=" + c.electric + ")");
    }
}

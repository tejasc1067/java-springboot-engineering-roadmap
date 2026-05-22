// The "two variables, same object" gotcha. If you've come from a language where
// `=` always copies (like C), this is a common source of confusion in Java.
//
// `b = a` does NOT copy the object. It copies the REFERENCE — both variables
// now point at the same underlying object.

class Car {
    String brand;
}

public class ReferenceVsValue {
    public static void main(String[] args) {
        Car a = new Car();
        a.brand = "Toyota";

        Car b = a;             // b now points to the SAME car as a
        b.brand = "Ford";      // mutating through b...

        System.out.println("a.brand = " + a.brand);   // ...changes what a sees
        System.out.println("b.brand = " + b.brand);
        System.out.println("a == b: " + (a == b));    // true — same reference

        // To get a real copy, you have to create a new object and copy fields manually.
        Car c = new Car();
        c.brand = a.brand;     // independent object with the same data
        c.brand = "Honda";

        System.out.println("\nAfter c is a separate copy:");
        System.out.println("a.brand = " + a.brand);   // still "Ford"
        System.out.println("c.brand = " + c.brand);   // "Honda"
        System.out.println("a == c: " + (a == c));    // false — different objects
    }
}

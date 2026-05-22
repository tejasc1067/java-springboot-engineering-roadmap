// What happens when you forget to initialize a reference.
// A declared-but-not-instantiated reference defaults to null.
// Using it crashes with NullPointerException — Java's most famous bug.

class Car {
    String brand;

    void describe() {
        System.out.println("Car: " + brand);
    }
}

public class NullReference {

    static Car globalCar;     // class field — defaults to null

    public static void main(String[] args) {

        // Case 1: deliberately trigger the NPE so you see what it looks like.
        try {
            globalCar.describe();
        } catch (NullPointerException e) {
            System.out.println("Caught NPE — globalCar was never assigned.");
        }

        // Case 2: the fix. Create the object before using it.
        globalCar = new Car();
        globalCar.brand = "Toyota";
        globalCar.describe();

        // Case 3: defensive check if a reference might legitimately be null.
        Car maybe = lookup("missing-key");
        if (maybe != null) {
            maybe.describe();
        } else {
            System.out.println("No car found for that key.");
        }
    }

    // Pretend this is a database lookup that may return null if nothing matches.
    static Car lookup(String key) {
        return null;
    }
}

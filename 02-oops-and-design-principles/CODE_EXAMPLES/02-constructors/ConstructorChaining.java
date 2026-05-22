// Chaining: one constructor calls another with `this(...)`.
// Eliminates duplicated assignment logic. The "main" constructor does the work;
// the rest fill in defaults and delegate.

class Car {
    String brand;
    int year;
    boolean electric;

    // The "main" constructor — every other one delegates to this.
    Car(String brand, int year, boolean electric) {
        System.out.println("  → main Car(String, int, boolean)");
        this.brand = brand;
        this.year = year;
        this.electric = electric;
    }

    Car(String brand, int year) {
        this(brand, year, false);   // must be the FIRST statement
        System.out.println("  → after this(...) call in Car(String, int)");
    }

    Car(String brand) {
        this(brand, 2024);
    }

    Car() {
        this("unknown");
    }
}

public class ConstructorChaining {
    public static void main(String[] args) {
        System.out.println("Creating new Car():");
        new Car();
        // Output traces the chain: Car() → Car("unknown") → Car("unknown", 2024)
        //                                                → Car("unknown", 2024, false)
    }
}

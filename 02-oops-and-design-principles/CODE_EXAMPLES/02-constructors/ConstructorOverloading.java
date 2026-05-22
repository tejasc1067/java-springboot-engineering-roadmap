// Multiple constructors with different parameter lists.
// The compiler picks one based on what you pass at the call site.

class Car {
    String brand;
    int year;

    Car() {                              // zero-arg
        this.brand = "unknown";
        this.year = 0;
    }

    Car(String brand) {                  // one-arg
        this.brand = brand;
        this.year = 2024;
    }

    Car(String brand, int year) {        // two-arg
        this.brand = brand;
        this.year = year;
    }

    void describe() {
        System.out.println(year + " " + brand);
    }
}

public class ConstructorOverloading {
    public static void main(String[] args) {
        new Car().describe();                       // 0 unknown
        new Car("Toyota").describe();               // 2024 Toyota
        new Car("Toyota", 2018).describe();         // 2018 Toyota
    }
}

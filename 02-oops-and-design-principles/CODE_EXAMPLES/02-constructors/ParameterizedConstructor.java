// Custom constructor: take the fields you need as parameters, assign them up front.
// Now you can't accidentally create a Car without a brand and year.

class Car {
    String brand;
    int year;

    // `this.brand` refers to the FIELD; `brand` (bare) refers to the parameter.
    // Without `this.`, the assignment would read "brand = brand" and the field
    // would remain null.
    Car(String brand, int year) {
        this.brand = brand;
        this.year = year;
    }
}

public class ParameterizedConstructor {
    public static void main(String[] args) {
        Car a = new Car("Toyota", 2018);
        Car b = new Car("Ford", 2020);
        System.out.println(a.year + " " + a.brand);
        System.out.println(b.year + " " + b.brand);

        // Try uncommenting this line — it fails to compile, because writing
        // your own constructor removes the free no-arg one.
        // Car c = new Car();
    }
}

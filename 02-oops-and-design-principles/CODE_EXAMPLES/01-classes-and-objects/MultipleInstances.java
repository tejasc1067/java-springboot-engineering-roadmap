// Three objects from the same class. Each has its OWN copy of `brand` and `year`.
// The class is written once; the data is per-object.

class Car {
    String brand;
    int year;

    void describe() {
        System.out.println(year + " " + brand);
    }
}

public class MultipleInstances {
    public static void main(String[] args) {
        Car a = new Car();
        a.brand = "Toyota"; a.year = 2018;

        Car b = new Car();
        b.brand = "Ford";   b.year = 2020;

        Car c = new Car();
        c.brand = "Honda";  c.year = 2022;

        a.describe();   // 2018 Toyota
        b.describe();   // 2020 Ford
        c.describe();   // 2022 Honda

        // Changing one object doesn't affect the others.
        a.year = 1999;
        a.describe();   // 1999 Toyota
        b.describe();   // still 2020 Ford
    }
}

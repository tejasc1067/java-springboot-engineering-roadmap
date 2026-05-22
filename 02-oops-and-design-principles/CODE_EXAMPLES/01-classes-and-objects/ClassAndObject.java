// The minimum-viable class: one field, one method, one object.
// Notice: the class is the blueprint, the object created with `new` is the instance.

class Car {
    String brand;

    void describe() {
        System.out.println("Car brand: " + brand);
    }
}

public class ClassAndObject {
    public static void main(String[] args) {
        Car car = new Car();      // create an object from the Car blueprint
        car.brand = "Toyota";     // set its data
        car.describe();           // call its behavior
    }
}

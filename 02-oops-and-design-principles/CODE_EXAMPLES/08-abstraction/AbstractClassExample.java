abstract class Vehicle {

    void start() {

        System.out.println(
                "Vehicle Started"
        );
    }
}

class Car extends Vehicle {

}

public class AbstractClassExample {

    public static void main(String[] args) {

        Car car = new Car();

        car.start();
    }
}
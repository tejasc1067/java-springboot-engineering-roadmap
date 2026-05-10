class Vehicle {

    void start() {

        System.out.println(
                "Vehicle Started"
        );
    }
}

class Car extends Vehicle {

    void drive() {

        System.out.println(
                "Car is Driving"
        );
    }
}

class Bike extends Vehicle {

    void ride() {

        System.out.println(
                "Bike is Riding"
        );
    }
}

public class HierarchicalInheritanceExample {

    public static void main(String[] args) {

        Car car = new Car();

        car.start();

        car.drive();

        Bike bike = new Bike();

        bike.start();

        bike.ride();
    }
}
class Vehicle {

    public void start() {

        System.out.println(
                "Vehicle Started"
        );
    }
}

class Car extends Vehicle {

    @Override
    public void start() {

        System.out.println(
                "Car Started"
        );
    }
}

public class OverrideAnnotationExample {

    public static void main(String[] args) {

        Vehicle vehicle =
                new Car();

        vehicle.start();
    }
}
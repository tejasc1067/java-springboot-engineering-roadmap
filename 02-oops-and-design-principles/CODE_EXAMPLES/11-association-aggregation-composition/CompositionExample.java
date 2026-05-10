class Engine {

    void startEngine() {

        System.out.println(
                "Engine Started"
        );
    }
}

class Car {

    private final Engine engine;

    Car() {

        engine = new Engine();
    }

    void startCar() {

        engine.startEngine();

        System.out.println(
                "Car Started"
        );
    }
}

public class CompositionExample {

    public static void main(String[] args) {

        Car car = new Car();

        car.startCar();
    }
}
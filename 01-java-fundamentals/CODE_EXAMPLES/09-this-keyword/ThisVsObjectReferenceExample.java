class Car {

    String carName;

    Car(String carName) {

        this.carName = carName;
    }

    void displayCar() {

        System.out.println("Car Name: " + this.carName);
    }
}

public class ThisVsObjectReferenceExample {

    public static void main(String[] args) {

        Car firstCar = new Car("BMW");

        firstCar.displayCar();
    }
}
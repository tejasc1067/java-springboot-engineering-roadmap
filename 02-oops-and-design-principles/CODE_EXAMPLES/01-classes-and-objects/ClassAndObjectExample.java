class Car {

    String brand;

    void displayBrand() {

        System.out.println(
                "Car Brand: " + brand
        );
    }
}

public class ClassAndObjectExample {

    public static void main(String[] args) {

        Car car1 = new Car();

        car1.brand = "BMW";

        car1.displayBrand();
    }
}
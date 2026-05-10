class Vehicle {

    String company = "Generic Vehicle Company";
}

class Car extends Vehicle {

    String company = "BMW";

    void displayCompanies() {

        System.out.println(
                "Current Object Company: "
                        + this.company
        );

        System.out.println(
                "Parent Object Company: "
                        + super.company
        );
    }
}

public class ThisVsSuperExample {

    public static void main(String[] args) {

        Car car = new Car();

        car.displayCompanies();
    }
}
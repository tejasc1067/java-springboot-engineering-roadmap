class Product {

    String productName =
            "Laptop";
}

public class ObjectEligibilityExample {

    public static void main(String[] args) {

        Product productOne =
                new Product();

        Product productTwo =
                productOne;

        productOne = null;

        productTwo = null;

        System.gc();

        System.out.println(
                "Object Became Unreachable"
        );
    }
}
class Product {

    String productName;
}

public class ObjectReferenceExample {

    public static void main(String[] args) {

        Product product1 = new Product();

        product1.productName = "Laptop";

        // product2 now references same object
        Product product2 = product1;

        product2.productName = "Gaming Laptop";

        System.out.println(
                "Product1: "
                        + product1.productName
        );

        System.out.println(
                "Product2: "
                        + product2.productName
        );
    }
}
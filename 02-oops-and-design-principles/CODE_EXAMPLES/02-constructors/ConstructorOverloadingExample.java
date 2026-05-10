class Product {

    int productId;

    String productName;

    Product() {

        System.out.println(
                "Default Product Constructor"
        );
    }

    Product(int productId) {

        this.productId = productId;

        System.out.println(
                "Product ID: " + productId
        );
    }

    Product(int productId, String productName) {

        this.productId = productId;

        this.productName = productName;

        System.out.println(
                "Product: "
                        + productId
                        + " - "
                        + productName
        );
    }
}

public class ConstructorOverloadingExample {

    public static void main(String[] args) {

        Product product1 = new Product();

        Product product2 = new Product(1);

        Product product3 =
                new Product(2, "Laptop");
    }
}
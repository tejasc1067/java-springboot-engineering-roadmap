final class Product {

    private final int productId;

    private final String productName;

    Product(
            int productId,
            String productName
    ) {

        this.productId =
                productId;

        this.productName =
                productName;
    }

    public int getProductId() {

        return productId;
    }

    public String getProductName() {

        return productName;
    }
}

public class FinalFieldExample {

    public static void main(String[] args) {

        Product product =
                new Product(
                        101,
                        "Laptop"
                );

        System.out.println(
                product.getProductId()
        );

        System.out.println(
                product.getProductName()
        );
    }
}
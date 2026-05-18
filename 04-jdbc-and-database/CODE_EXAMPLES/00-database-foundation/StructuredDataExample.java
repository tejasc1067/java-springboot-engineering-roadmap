class Product {

    int id;
    String name;
    double price;

    Product(
            int id,
            String name,
            double price
    ) {

        this.id = id;
        this.name = name;
        this.price = price;
    }
}

public class StructuredDataExample {

    public static void main(String[] args) {

        Product product =
                new Product(
                        1,
                        "Laptop",
                        75000
                );

        System.out.println(
                product.name
        );
    }
}
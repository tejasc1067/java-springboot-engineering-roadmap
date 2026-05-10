class Product {

    int productId;

    Product(int productId) {

        this.productId = productId;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {

            return true;
        }

        if (obj == null
                || getClass() != obj.getClass()) {

            return false;
        }

        Product product =
                (Product) obj;

        return productId
                == product.productId;
    }

    @Override
    public int hashCode() {

        return Integer.hashCode(productId);
    }
}

public class HashCodeExample {

    public static void main(String[] args) {

        Product product1 =
                new Product(101);

        Product product2 =
                new Product(101);

        System.out.println(
                product1.hashCode()
        );

        System.out.println(
                product2.hashCode()
        );
    }
}
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class ProductCache {

    private final ReadWriteLock lock =
            new ReentrantReadWriteLock();

    private String product =
            "Laptop";

    void readProduct() {

        lock.readLock().lock();

        try {

            System.out.println(
                    "Reading Product: "
                            + product
            );

        } finally {

            lock.readLock().unlock();
        }
    }

    void updateProduct(String newProduct) {

        lock.writeLock().lock();

        try {

            product = newProduct;

            System.out.println(
                    "Updated Product"
            );

        } finally {

            lock.writeLock().unlock();
        }
    }
}

public class ReadWriteLockExample {

    public static void main(String[] args) {

        ProductCache cache =
                new ProductCache();

        cache.readProduct();

        cache.updateProduct("Mobile");

        cache.readProduct();
    }
}
package com.example.rediscache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/** Stands in for the database. Counts reads so tests can tell a cache hit from a miss. */
@Component
public class ProductCatalog {

    private final Map<Long, Product> rows = new ConcurrentHashMap<>(Map.of(
            1L, new Product(1, "Keyboard", 4_999),
            2L, new Product(2, "Mouse", 1_999)));
    private final AtomicInteger reads = new AtomicInteger();

    public Product load(long id) {
        reads.incrementAndGet();
        return rows.get(id);
    }

    public void save(Product product) {
        rows.put(product.id(), product);
    }

    public int reads() {
        return reads.get();
    }
}

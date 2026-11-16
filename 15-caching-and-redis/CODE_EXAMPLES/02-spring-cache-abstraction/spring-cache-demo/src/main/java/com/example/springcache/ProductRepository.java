package com.example.springcache;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** Stands in for a database table. Reads are counted: a cache hit is a read that didn't happen. */
@Component
public class ProductRepository {

    private final Map<Long, Product> rows = new HashMap<>();
    private int reads;

    public Product findById(long id) {
        reads++;
        return rows.get(id);
    }

    public Product save(Product product) {
        rows.put(product.id(), product);
        return product;
    }

    public void deleteById(long id) {
        rows.remove(id);
    }

    public int reads() {
        return reads;
    }

    public void reset() {
        rows.clear();
        reads = 0;
    }
}

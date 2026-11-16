package com.example.stampede;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final SlowDatabase database;

    public ProductService(SlowDatabase database) {
        this.database = database;
    }

    @Cacheable(cacheNames = "products", unless = "#result == null")
    public String find(long id) {
        return database.product(id);
    }

    @Cacheable("productsWithNegativeCaching")
    public String findWithNegativeCaching(long id) {
        return database.product(id);
    }

    @Cacheable(cacheNames = "productsWithNegativeCaching", sync = true)
    public String findWithNegativeCachingSync(long id) {
        return database.product(id);
    }
}

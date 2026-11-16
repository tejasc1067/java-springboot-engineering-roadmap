package com.example.rediscache;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductCatalog catalog;

    public ProductService(ProductCatalog catalog) {
        this.catalog = catalog;
    }

    // The JSON config disables caching nulls, so a missing product must be skipped explicitly.
    @Cacheable(cacheNames = "products", unless = "#result == null")
    public Product find(long id) {
        return catalog.load(id);
    }

    @CachePut(cacheNames = "products", key = "#product.id")
    public Product update(Product product) {
        catalog.save(product);
        return product;
    }
}

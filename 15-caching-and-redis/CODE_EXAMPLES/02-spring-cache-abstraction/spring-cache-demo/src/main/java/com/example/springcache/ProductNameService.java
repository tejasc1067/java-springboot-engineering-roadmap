package com.example.springcache;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ProductNameService {

    private final ProductRepository repository;

    public ProductNameService(ProductRepository repository) {
        this.repository = repository;
    }

    // Broken: same cache as ProductService.findById, same default key (the id),
    // different return type. Whichever method runs first decides what's stored under key 1.
    @Cacheable("products")
    public String nameOfSharingCache(long id) {
        return repository.findById(id).name();
    }

    // Fixed: its own cache name (an explicit key prefix like key = "'name:' + #id" also works).
    @Cacheable("product-names")
    public String nameOf(long id) {
        return repository.findById(id).name();
    }
}

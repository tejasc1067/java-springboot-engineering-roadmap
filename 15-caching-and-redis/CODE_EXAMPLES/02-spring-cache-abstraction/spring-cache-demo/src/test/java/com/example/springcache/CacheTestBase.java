package com.example.springcache;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

/** One shared Spring context; every test starts with empty caches and a fresh "table". */
@SpringBootTest
abstract class CacheTestBase {

    @Autowired ProductRepository repository;
    @Autowired ProductService productService;
    @Autowired CacheManager cacheManager;

    @BeforeEach
    void resetCachesAndTable() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        repository.reset();
        repository.save(new Product(1, "Kettle", 2500));
        repository.save(new Product(2, "Mug", 800));
    }
}

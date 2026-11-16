package com.example.springcache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    // Key defaults to the only parameter, id. "Not found" (null) is not cached.
    @Cacheable(cacheNames = "products", unless = "#result == null")
    public Product findById(long id) {
        return repository.findById(id);
    }

    // Broken: the default caches null too, so a product created later stays "not found".
    @Cacheable("products-with-misses")
    public Product findByIdCachingMisses(long id) {
        return repository.findById(id);
    }

    // Always runs, then puts the return value into the cache under the product's id.
    @CachePut(cacheNames = "products", key = "#product.id")
    public Product save(Product product) {
        return repository.save(product);
    }

    @CacheEvict("products")
    public void delete(long id) {
        repository.deleteById(id);
    }

    @CacheEvict(cacheNames = "products", allEntries = true)
    public void evictAll() {
    }

    // Broken: this.findById is a plain Java call, not a call through the caching proxy.
    public List<Product> findAllSelfInvoking(List<Long> ids) {
        return ids.stream().map(this::findById).toList();
    }

    // Broken: with two parameters the default key is SimpleKey[id, priceCents], which never matches
    // the entry findById stored under id. Nothing is evicted, exception or not.
    @CacheEvict("products")
    public void changePriceWithDefaultKey(long id, long priceCents) {
        Product current = repository.findById(id);
        repository.save(new Product(id, current.name(), priceCents));
    }

    // Broken: if the method throws after the write, the default (evict after the call) never evicts.
    @CacheEvict(cacheNames = "products", key = "#id")
    public void changePriceThenNotify(long id, long priceCents) {
        Product current = repository.findById(id);
        repository.save(new Product(id, current.name(), priceCents));
        throw new IllegalStateException("price-change notification failed");
    }

    // Fixed: evicts before the method runs, so it happens even when the method throws.
    @CacheEvict(cacheNames = "products", key = "#id", beforeInvocation = true)
    public void changePriceThenNotifyEvictingFirst(long id, long priceCents) {
        Product current = repository.findById(id);
        repository.save(new Product(id, current.name(), priceCents));
        throw new IllegalStateException("price-change notification failed");
    }
}

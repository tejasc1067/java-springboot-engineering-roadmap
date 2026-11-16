package com.example.stampede;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class PriceService {

    private final SlowDatabase database;

    public PriceService(SlowDatabase database) {
        this.database = database;
    }

    @Cacheable("prices")
    public Long price(long productId) {
        return database.price(productId);
    }

    @Cacheable(cacheNames = "prices", sync = true)
    public Long priceSync(long productId) {
        return database.price(productId);
    }

    @Cacheable(cacheNames = "prices", sync = true, cacheManager = "lockingCacheManager")
    public Long priceSyncWithLockingWriter(long productId) {
        return database.price(productId);
    }

    @Cacheable("pricesWithJitter")
    public Long priceWithJitter(long productId) {
        return database.price(productId);
    }
}

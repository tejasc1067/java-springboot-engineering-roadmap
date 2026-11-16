package com.example.caffeine;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PriceService {

    private final JdbcTemplate jdbc;
    private final AtomicInteger databaseReads = new AtomicInteger();

    public PriceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Cacheable("prices")
    public long price(String sku) {
        databaseReads.incrementAndGet();
        return jdbc.queryForObject("SELECT cents FROM price WHERE sku = ?", Long.class, sku);
    }

    /** Evicts the entry - but only from the cache inside this instance. */
    @CacheEvict(cacheNames = "prices", key = "#sku")
    public void updatePrice(String sku, long cents) {
        jdbc.update("MERGE INTO price (sku, cents) KEY (sku) VALUES (?, ?)", sku, cents);
    }

    public int databaseReads() {
        return databaseReads.get();
    }
}

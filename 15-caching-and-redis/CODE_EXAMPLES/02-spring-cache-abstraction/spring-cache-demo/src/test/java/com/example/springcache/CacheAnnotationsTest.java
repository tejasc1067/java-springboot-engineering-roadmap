package com.example.springcache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class CacheAnnotationsTest extends CacheTestBase {

    @Test
    void bootPicksTheSimpleInMemoryCacheManager_whenNoCacheLibraryIsOnTheClasspath() {
        assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
    }

    @Test
    void cacheable_secondCallWithTheSameKey_doesNotRunTheMethod() {
        productService.findById(1);
        productService.findById(1);
        productService.findById(1);

        assertThat(repository.reads()).isEqualTo(1);
    }

    @Test
    void cacheable_aDifferentKeyIsADifferentEntry() {
        productService.findById(1);
        productService.findById(2);

        assertThat(repository.reads()).isEqualTo(2);
    }

    @Test
    void theCacheIsAMapYouCanLookInto_keyedByTheMethodArgument() {
        productService.findById(1);

        assertThat(cacheManager.getCache("products").get(1L, Product.class))
                .isEqualTo(new Product(1, "Kettle", 2500));
    }

    @Test
    void cachePut_alwaysRunsTheMethod_andReplacesTheCachedValue() {
        productService.findById(1);                                   // cached at 25.00

        productService.save(new Product(1, "Kettle", 1999));          // runs, then puts the result

        assertThat(productService.findById(1).priceCents()).isEqualTo(1999);
        assertThat(repository.reads()).isEqualTo(1);                  // the new value came from the put
    }

    @Test
    void cacheEvict_removesOneEntry_soTheNextCallReloads() {
        productService.findById(1);
        productService.findById(2);

        productService.delete(1);

        assertThat(productService.findById(1)).isNull();              // reloaded: gone from the table
        productService.findById(2);                                    // still cached
        assertThat(repository.reads()).isEqualTo(3);
    }

    @Test
    void cacheEvictAllEntries_emptiesTheWholeCache() {
        productService.findById(1);
        productService.findById(2);

        productService.evictAll();
        productService.findById(1);
        productService.findById(2);

        assertThat(repository.reads()).isEqualTo(4);
    }
}

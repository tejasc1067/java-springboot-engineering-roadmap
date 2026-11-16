package com.example.springcache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NullCachingTest extends CacheTestBase {

    @Test
    void byDefault_nullIsCached_soAProductCreatedLaterStaysNotFound() {
        assertThat(productService.findByIdCachingMisses(3)).isNull();   // no product 3 yet: null is cached

        repository.save(new Product(3, "Teapot", 3200));                 // created, e.g. by an admin screen

        assertThat(productService.findByIdCachingMisses(3)).isNull();   // still "not found"
        assertThat(repository.reads()).isEqualTo(1);
    }

    @Test
    void unlessResultIsNull_misses_areNotCached() {
        assertThat(productService.findById(3)).isNull();

        repository.save(new Product(3, "Teapot", 3200));

        assertThat(productService.findById(3)).isEqualTo(new Product(3, "Teapot", 3200));
    }
}

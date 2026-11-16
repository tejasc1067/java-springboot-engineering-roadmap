package com.example.springcache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SelfInvocationTest extends CacheTestBase {

    @Autowired ProductPageService productPageService;

    @Test
    void callingACachedMethodFromTheSameClass_skipsTheCache() {
        productService.findAllSelfInvoking(List.of(1L, 2L));
        productService.findAllSelfInvoking(List.of(1L, 2L));

        assertThat(repository.reads()).isEqualTo(4);   // nothing was cached, nothing was read from cache
        assertThat(cacheManager.getCache("products").get(1L)).isNull();
    }

    @Test
    void callingItFromAnotherBean_goesThroughTheProxy() {
        productPageService.findAll(List.of(1L, 2L));
        productPageService.findAll(List.of(1L, 2L));

        assertThat(repository.reads()).isEqualTo(2);
    }
}

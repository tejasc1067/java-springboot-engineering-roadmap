package com.example.springcache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvictOnExceptionTest extends CacheTestBase {

    @Test
    void defaultEvict_runsOnlyAfterSuccess_soAWriteFollowedByAnExceptionLeavesTheCacheStale() {
        productService.findById(1);                                              // cached at 25.00

        assertThatThrownBy(() -> productService.changePriceThenNotify(1, 1999))
                .isInstanceOf(IllegalStateException.class);

        assertThat(repository.findById(1).priceCents()).isEqualTo(1999);       // the table changed
        assertThat(productService.findById(1).priceCents()).isEqualTo(2500);   // the cache didn't
    }

    @Test
    void beforeInvocationTrue_evictsEvenWhenTheMethodThrows() {
        productService.findById(1);

        assertThatThrownBy(() -> productService.changePriceThenNotifyEvictingFirst(1, 1999))
                .isInstanceOf(IllegalStateException.class);

        assertThat(productService.findById(1).priceCents()).isEqualTo(1999);
    }
}

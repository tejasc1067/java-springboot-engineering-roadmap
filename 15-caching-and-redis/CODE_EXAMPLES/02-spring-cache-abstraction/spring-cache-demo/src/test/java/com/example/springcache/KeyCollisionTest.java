package com.example.springcache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyCollisionTest extends CacheTestBase {

    @Autowired ProductNameService productNameService;

    @Test
    void twoMethodsShareACacheAndTheDefaultKey_theSecondGetsTheFirstOnesValue() {
        productService.findById(1);                    // stores a Product under key 1 in "products"

        assertThatThrownBy(() -> productNameService.nameOfSharingCache(1))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void multipleParameters_theDefaultKeyIsAllOfThem_soTheEvictMissesTheEntry() {
        productService.findById(1);                                   // stored under key 1

        productService.changePriceWithDefaultKey(1, 1999);            // evicts SimpleKey [1,1999]

        assertThat(productService.findById(1).priceCents()).isEqualTo(2500);   // stale
    }

    @Test
    void separateCacheNames_noCollision() {
        productService.findById(1);

        assertThat(productNameService.nameOf(1)).isEqualTo("Kettle");
    }
}

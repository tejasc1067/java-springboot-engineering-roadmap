package com.example.sharding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScatterGatherTest {

    private final OrderShards orders = new OrderShards(8);

    @BeforeEach
    void load() {
        for (int i = 0; i < 1_000; i++) {
            orders.save(new OrderShards.Order("order-" + i, "customer-" + (i % 100), "product-" + (i % 7)));
        }
        orders.resetCounter();
    }

    @Test
    void aQueryByTheShardKey_asksOneShard() {
        assertThat(orders.byCustomer("customer-42")).hasSize(10);
        assertThat(orders.shardsQueried()).isEqualTo(1);
    }

    @Test
    void aQueryByAnythingElse_asksEveryShard() {
        assertThat(orders.byProduct("product-3")).hasSize(143);
        assertThat(orders.shardsQueried()).isEqualTo(8);   // and it's as slow as the slowest shard
    }
}

package com.example.sharding;

import java.util.ArrayList;
import java.util.List;

/** Orders sharded by customer id. Fine for "my orders", painful for anything else. */
public final class OrderShards {

    public record Order(String id, String customerId, String productId) { }

    private final List<List<Order>> shards = new ArrayList<>();
    private int shardsQueried;

    public OrderShards(int shardCount) {
        for (int i = 0; i < shardCount; i++) shards.add(new ArrayList<>());
    }

    public void save(Order order) {
        shards.get(ModuloSharding.shardFor(order.customerId(), shards.size())).add(order);
    }

    /** Uses the shard key: exactly one shard is asked. */
    public List<Order> byCustomer(String customerId) {
        shardsQueried++;
        return shards.get(ModuloSharding.shardFor(customerId, shards.size())).stream()
                .filter(o -> o.customerId().equals(customerId)).toList();
    }

    /** Not the shard key: every shard must be asked and the results merged (scatter-gather). */
    public List<Order> byProduct(String productId) {
        List<Order> result = new ArrayList<>();
        for (List<Order> shard : shards) {
            shardsQueried++;
            shard.stream().filter(o -> o.productId().equals(productId)).forEach(result::add);
        }
        return result;
    }

    public int shardsQueried() {
        return shardsQueried;
    }

    public void resetCounter() {
        shardsQueried = 0;
    }
}

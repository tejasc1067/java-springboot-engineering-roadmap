package com.example.sharding;

/** shard = hash(key) % N. Perfectly simple, until N changes. */
public final class ModuloSharding {

    private ModuloSharding() { }

    public static int shardFor(String key, int shardCount) {
        return (int) Math.floorMod(Hashing.hash(key), (long) shardCount);
    }
}

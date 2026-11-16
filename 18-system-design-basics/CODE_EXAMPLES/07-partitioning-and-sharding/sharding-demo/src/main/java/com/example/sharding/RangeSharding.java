package com.example.sharding;

/** Shard by ranges of a numeric id: [0, 1M) on shard 0, [1M, 2M) on shard 1, and so on. */
public final class RangeSharding {

    private final long rangeSize;
    private final int shardCount;

    public RangeSharding(long rangeSize, int shardCount) {
        this.rangeSize = rangeSize;
        this.shardCount = shardCount;
    }

    public int shardFor(long id) {
        return (int) Math.min(id / rangeSize, shardCount - 1);
    }
}

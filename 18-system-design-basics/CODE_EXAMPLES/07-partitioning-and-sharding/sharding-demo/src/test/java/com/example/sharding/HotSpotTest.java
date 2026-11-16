package com.example.sharding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class HotSpotTest {

    @Test
    void rangeShardingOnAGrowingId_sendsEveryNewWriteToTheLastShard() {
        RangeSharding byRange = new RangeSharding(1_000_000, 4);
        Map<Integer, Integer> today = new HashMap<>();
        // Today's 100,000 new orders get the next ids, 3,000,000 to 3,099,999.
        for (long id = 3_000_000; id < 3_100_000; id++) today.merge(byRange.shardFor(id), 1, Integer::sum);

        assertThat(today).containsOnly(Map.entry(3, 100_000));   // three shards idle, one doing all the work
    }

    @Test
    void hashingTheSameIds_spreadsTheNewWrites() {
        Map<Integer, Integer> today = new HashMap<>();
        for (long id = 3_000_000; id < 3_100_000; id++) {
            today.merge(ModuloSharding.shardFor(Long.toString(id), 4), 1, Integer::sum);
        }

        assertThat(today).hasSize(4);
        assertThat(today.values()).allSatisfy(n -> assertThat(n).isBetween(24_000, 26_000));
    }

    @Test
    void aCelebrityKey_overloadsOneShard_whateverTheHashing() {
        Map<Integer, Integer> load = new HashMap<>();
        for (String author : postAuthors()) load.merge(ModuloSharding.shardFor(author, 8), 1, Integer::sum);

        int busiest = load.values().stream().max(Integer::compare).orElseThrow();
        // The celebrity's shard takes 47,207 of 100,000 writes; the other seven take about 7,500 each.
        assertThat(busiest).isEqualTo(47_207);
    }

    @Test
    void saltingTheHotKey_spreadsItsWrites_butItsReadsMustAskEverySalt() {
        Map<Integer, Integer> load = new HashMap<>();
        int post = 0;
        for (String author : postAuthors()) {
            // Only the known hot key is split: "celebrity#0" .. "celebrity#7".
            String key = author.equals("celebrity") ? author + "#" + (post++ % 8) : author;
            load.merge(ModuloSharding.shardFor(key, 8), 1, Integer::sum);
        }

        int busiest = load.values().stream().max(Integer::compare).orElseThrow();
        // Busiest shard: 22,387, down from 47,207. Not 12,500: with 8 salts on 8 shards, some salts share a
        // shard (shard 0 got three of them). More salts than shards spreads it further.
        assertThat(busiest).isEqualTo(22_387);
        // The price: reading the celebrity's posts now means 8 lookups instead of 1.
    }

    /** 100,000 posts: 40% by one celebrity, the rest by 10,000 ordinary users. Seeded, so always the same. */
    private static List<String> postAuthors() {
        Random random = new Random(42);
        List<String> authors = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            authors.add(random.nextInt(100) < 40 ? "celebrity" : "user-" + random.nextInt(10_000));
        }
        return authors;
    }
}

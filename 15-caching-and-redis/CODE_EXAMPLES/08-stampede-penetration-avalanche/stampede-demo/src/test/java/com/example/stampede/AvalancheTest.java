package com.example.stampede;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LongSummaryStatistics;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Warm 200 keys in one go, as a deploy, a cache flush, or a nightly preload does, and look at when they expire. */
@SpringBootTest
class AvalancheTest extends RedisContainerSupport {

    @Autowired PriceService prices;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void reset() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void sameTtl_everyKeyExpiresInTheSameSecond() {
        LongStream.rangeClosed(1, 200).forEach(prices::price);

        LongSummaryStatistics expiry = remainingMillis("prices::*");

        // Ten minutes from now, all 200 miss together and all 200 queries hit the database at once.
        assertThat(expiry.getMax() - expiry.getMin()).isLessThan(1_000);
    }

    @Test
    void ttlWithJitter_spreadsExpiryOverMinutes() {
        LongStream.rangeClosed(1, 200).forEach(prices::priceWithJitter);

        LongSummaryStatistics expiry = remainingMillis("pricesWithJitter::*");

        assertThat(expiry.getMin()).isGreaterThanOrEqualTo(590_000);
        assertThat(expiry.getMax() - expiry.getMin()).isGreaterThan(60_000);
    }

    private LongSummaryStatistics remainingMillis(String pattern) {
        return redis.keys(pattern).stream()
                .mapToLong(key -> redis.getExpire(key, TimeUnit.MILLISECONDS))
                .summaryStatistics();
    }
}

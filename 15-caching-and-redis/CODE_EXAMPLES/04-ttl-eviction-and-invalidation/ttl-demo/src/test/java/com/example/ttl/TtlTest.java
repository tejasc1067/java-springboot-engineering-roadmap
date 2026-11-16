package com.example.ttl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TtlTest {

    private final PriceDatabase db = new PriceDatabase();
    private final FakeTicker clock = new FakeTicker();

    @Test
    void staleRead_cacheKeepsServingTheOldPriceUntilTheTtlRunsOut() {
        db.update("book", 1_000);
        LoadingCache<String, Long> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .ticker(clock)
                .build(db::load);

        assertThat(cache.get("book")).isEqualTo(1_000);

        db.update("book", 1_500);                        // the price changes in the database
        clock.advance(Duration.ofMinutes(4));
        assertThat(cache.get("book")).isEqualTo(1_000);  // stale: 4 minutes old, still "fresh" to the cache

        clock.advance(Duration.ofMinutes(1));
        assertThat(cache.get("book")).isEqualTo(1_500);  // 5 minutes: expired, reloaded
        assertThat(db.queries()).isEqualTo(2);
    }

    @Test
    void expireAfterAccess_aHotKeyIsNeverRefreshed() {
        db.update("book", 1_000);
        LoadingCache<String, Long> cache = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(5))     // idle timeout, not an age limit
                .ticker(clock)
                .build(db::load);

        cache.get("book");
        db.update("book", 1_500);

        // Read once a minute for an hour: every read resets the 5-minute idle timer.
        for (int minute = 0; minute < 60; minute++) {
            clock.advance(Duration.ofMinutes(1));
            assertThat(cache.get("book")).isEqualTo(1_000);
        }
        assertThat(db.queries()).isEqualTo(1);       // an hour stale, and it would stay stale forever
    }

    @Test
    void expireAfterWrite_capsHowStaleAHotKeyCanGet() {
        db.update("book", 1_000);
        LoadingCache<String, Long> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .ticker(clock)
                .build(db::load);

        cache.get("book");
        db.update("book", 1_500);

        int staleReads = 0;
        for (int minute = 0; minute < 60; minute++) {
            clock.advance(Duration.ofMinutes(1));
            if (cache.get("book") == 1_000) staleReads++;
        }
        assertThat(staleReads).isEqualTo(4);          // minutes 1-4; from minute 5 on it's fresh
        assertThat(db.queries()).isEqualTo(13);       // one reload every 5 minutes: 1 + 60/5
    }
}

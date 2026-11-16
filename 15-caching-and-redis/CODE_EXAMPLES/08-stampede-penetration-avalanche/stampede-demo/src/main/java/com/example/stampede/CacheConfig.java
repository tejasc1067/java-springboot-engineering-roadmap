package com.example.stampede;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
public class CacheConfig {

    static final Duration TTL = Duration.ofMinutes(10);

    @Bean
    @Primary
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig().entryTtl(TTL);
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        // Penetration, broken: "not found" is never cached, so every lookup goes to the database.
                        "products", defaults.disableCachingNullValues(),
                        // Penetration, fixed: "not found" is cached too, but only briefly.
                        "productsWithNegativeCaching", defaults.entryTtl((key, value) ->
                                value == null ? Duration.ofSeconds(30) : TTL),
                        // Avalanche, fixed: every entry gets 10 minutes plus up to 2 random minutes.
                        "pricesWithJitter", defaults.entryTtl((key, value) ->
                                TTL.plusSeconds(ThreadLocalRandom.current().nextLong(120)))))
                .build();
    }

    /**
     * A cache manager whose writer takes a lock in Redis around every load. That lock is per cache NAME:
     * it holds back other instances too, but also every other key of the same cache.
     */
    @Bean
    RedisCacheManager lockingCacheManager(RedisConnectionFactory connectionFactory) {
        return RedisCacheManager.builder(RedisCacheWriter.lockingRedisCacheWriter(connectionFactory))
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig().entryTtl(TTL))
                .build();
    }
}

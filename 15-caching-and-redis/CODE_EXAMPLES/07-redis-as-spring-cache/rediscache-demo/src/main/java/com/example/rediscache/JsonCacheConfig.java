package com.example.rediscache;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * The production-style setup. Without the "json" profile the app runs on Boot's defaults:
 * JDK serialization and no TTL at all.
 */
@Configuration
@Profile("json")
public class JsonCacheConfig {

    // Bump this when a cached class changes incompatibly: new code then never reads old entries.
    @Value("${app.cache.version:v1}")
    private String cacheVersion;

    // Boot uses a RedisCacheConfiguration bean as the default for every cache.
    @Bean
    RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .prefixCacheNameWith(cacheVersion + ":")
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }

    // Per-cache overrides: prices change often, so products get a shorter TTL than the default.
    @Bean
    RedisCacheManagerBuilderCustomizer perCacheTtl(RedisCacheConfiguration defaults) {
        return builder -> builder.withCacheConfiguration("products", defaults.entryTtl(Duration.ofMinutes(10)));
    }
}

package com.example.messaging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * One instance's in-memory cache (like topic 05's Caffeine cache), kept consistent with the other
 * instances by broadcasting evictions over Redis Pub/Sub. Every instance subscribes to the channel;
 * whichever instance changes a product publishes its id, and all of them, itself included, evict it.
 */
public class LocalProductCache implements AutoCloseable {

    public static final String CHANNEL = "cache:evict:product";

    private final Map<String, String> entries = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer listener;

    public LocalProductCache(RedisConnectionFactory connectionFactory, StringRedisTemplate redis) {
        this.redis = redis;
        this.listener = new RedisMessageListenerContainer();
        listener.setConnectionFactory(connectionFactory);
        listener.addMessageListener((message, pattern) -> entries.remove(new String(message.getBody())),
                new ChannelTopic(CHANNEL));
        listener.afterPropertiesSet();
        listener.start();
    }

    public String get(String productId, Function<String, String> loadFromDatabase) {
        return entries.computeIfAbsent(productId, loadFromDatabase);
    }

    /** Call after the database write commits. */
    public void evictEverywhere(String productId) {
        redis.convertAndSend(CHANNEL, productId);
    }

    @Override
    public void close() throws Exception {
        listener.destroy();
    }
}

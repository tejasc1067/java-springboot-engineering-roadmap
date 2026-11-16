package com.example.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * One real Redis for every test class. Started once in a static block (not @Container),
 * so all classes share it along with Spring's cached test context.
 */
@SpringBootTest
abstract class RedisTestBase {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void emptyRedis() {
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }
}

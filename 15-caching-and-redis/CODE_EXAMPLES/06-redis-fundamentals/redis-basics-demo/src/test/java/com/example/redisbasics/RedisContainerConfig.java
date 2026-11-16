package com.example.redisbasics;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/**
 * One real Redis for every test class. @ServiceConnection(name = "redis") tells Spring Boot
 * "this container is Redis": it reads the host and mapped port and points StringRedisTemplate at it.
 */
@TestConfiguration(proxyBeanMethods = false)
class RedisContainerConfig {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    }
}

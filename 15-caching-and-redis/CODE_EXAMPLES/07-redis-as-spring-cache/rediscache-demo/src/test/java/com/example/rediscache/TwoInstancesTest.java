package com.example.rediscache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Two complete application contexts, as if two instances were running behind a load balancer.
 * Each has its own ProductCatalog; the only thing they share is Redis.
 */
class TwoInstancesTest extends RedisContainerSupport {

    static ConfigurableApplicationContext instanceA;
    static ConfigurableApplicationContext instanceB;

    @BeforeAll
    static void startTwoInstances() {
        instanceA = start();
        instanceB = start();
        instanceA.getBean(StringRedisTemplate.class).getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(RedisCacheDemoApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("json")
                .properties("spring.data.redis.host=" + REDIS.getHost(),
                        "spring.data.redis.port=" + REDIS.getMappedPort(6379))
                .run();
    }

    @AfterAll
    static void stop() {
        instanceA.close();
        instanceB.close();
    }

    @Test
    void anUpdateOnInstanceA_isWhatInstanceBReads() {
        ProductService a = instanceA.getBean(ProductService.class);
        ProductService b = instanceB.getBean(ProductService.class);
        ProductCatalog catalogB = instanceB.getBean(ProductCatalog.class);

        assertThat(b.find(2).priceCents()).isEqualTo(1_999);           // B caches the old price
        a.update(new Product(2, "Mouse", 2_499));                     // A changes it (@CachePut)
        int readsBefore = catalogB.reads();

        assertThat(b.find(2).priceCents()).isEqualTo(2_499);           // B sees A's write...
        assertThat(catalogB.reads()).isEqualTo(readsBefore);          // ...from Redis, not its own database
    }
}

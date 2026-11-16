package com.example.caffeine;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cheapest fix for diverging local caches: a short TTL. It doesn't remove the staleness,
 * it puts an upper bound on it. Here: 30 seconds, measured on a clock the test controls.
 */
class ShortTtlTest {

    static final AtomicLong nanos = new AtomicLong();

    /** Not a @Configuration, so component scanning doesn't pick it up for the other tests. */
    static class ShortTtl {
        @Bean
        Caffeine<Object, Object> caffeine() {
            // When spring.cache.caffeine.spec is empty, Spring Boot builds every cache from this bean.
            return Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofSeconds(30))
                    .ticker(nanos::get);
        }
    }

    private final ConfigurableApplicationContext instanceA = start();
    private final ConfigurableApplicationContext instanceB = start();

    private static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(CaffeineDemoApplication.class, ShortTtl.class)
                .web(WebApplicationType.NONE)
                .properties("spring.jmx.enabled=false")
                // A command-line argument, because .properties(...) are only defaults and
                // application.yml's spec would win over them.
                .run("--spring.cache.caffeine.spec=");
    }

    @AfterEach
    void stop() {
        instanceA.close();
        instanceB.close();
    }

    @Test
    void shortTtl_boundsHowLongTheOtherInstanceStaysStale() {
        PriceService a = instanceA.getBean(PriceService.class);
        PriceService b = instanceB.getBean(PriceService.class);
        a.updatePrice("mug", 800);
        b.price("mug");

        a.updatePrice("mug", 900);

        nanos.addAndGet(Duration.ofSeconds(29).toNanos());
        assertThat(b.price("mug")).isEqualTo(800);        // still stale...

        nanos.addAndGet(Duration.ofSeconds(1).toNanos());
        assertThat(b.price("mug")).isEqualTo(900);        // ...but never for more than 30 seconds
    }
}

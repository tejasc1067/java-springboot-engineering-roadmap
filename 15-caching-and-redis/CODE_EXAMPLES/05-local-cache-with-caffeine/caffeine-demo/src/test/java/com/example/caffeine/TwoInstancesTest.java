package com.example.caffeine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two instances of the same service, behind a load balancer, sharing one database.
 * Each instance is a whole Spring application context with its own Caffeine cache.
 */
class TwoInstancesTest {

    private final ConfigurableApplicationContext instanceA = start();
    private final ConfigurableApplicationContext instanceB = start();

    private static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(CaffeineDemoApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.jmx.enabled=false")
                .run();
    }

    @AfterEach
    void stop() {
        instanceA.close();
        instanceB.close();
    }

    @Test
    void localCaches_divergeAfterAnUpdate() {
        PriceService a = instanceA.getBean(PriceService.class);
        PriceService b = instanceB.getBean(PriceService.class);

        a.updatePrice("book", 1_000);
        assertThat(a.price("book")).isEqualTo(1_000);
        assertThat(b.price("book")).isEqualTo(1_000);      // both instances have it cached now

        a.updatePrice("book", 1_500);                      // the request happened to land on A

        assertThat(a.price("book")).isEqualTo(1_500);      // A evicted its own copy and reloaded
        assertThat(b.price("book")).isEqualTo(1_000);      // B never heard about it: stale for up to 10 minutes
        // A user refreshing the page sees 1,500, then 1,000, then 1,500, depending on which instance answers.
    }

    @Test
    void eachInstanceLoadsAndStoresItsOwnCopy() {
        PriceService a = instanceA.getBean(PriceService.class);
        PriceService b = instanceB.getBean(PriceService.class);
        a.updatePrice("pen", 200);

        a.price("pen");
        b.price("pen");

        // Two database reads and two copies in memory for one value. With 10 instances, 10 of each.
        assertThat(a.databaseReads() + b.databaseReads()).isEqualTo(2);
    }
}

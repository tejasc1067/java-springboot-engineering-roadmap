package com.example.probes;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Stands in for Spring Boot's own "db" indicator (which runs SELECT 1 against the DataSource),
 * so the tests can switch the database "off" and "on" at an exact moment.
 * Its component name in the health JSON is "database" (bean name minus "HealthIndicator").
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final AtomicBoolean reachable = new AtomicBoolean(true);

    public void setReachable(boolean value) {
        reachable.set(value);
    }

    @Override
    public Health health() {
        return reachable.get()
                ? Health.up().build()
                : Health.down().withDetail("error", "Connection refused: db:5432").build();
    }
}

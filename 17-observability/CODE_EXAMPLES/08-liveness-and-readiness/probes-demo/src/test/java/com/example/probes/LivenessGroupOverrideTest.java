package com.example.probes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Broken: configuring the liveness group without "include". Any group you configure replaces Boot's
 * built-in probe group, and a group with no include list contains every health indicator.
 */
class LivenessGroupOverrideTest {

    @Test
    void touchingTheLivenessGroupWithoutInclude_putsTheDatabaseIntoLiveness() {
        try (ConfigurableApplicationContext app = new SpringApplicationBuilder(ProbesApplication.class).run(
                "--spring.config.name=none",   // ignore application.yml: only the settings below apply
                "--server.port=0",
                "--management.endpoint.health.probes.enabled=true",
                "--management.endpoint.health.group.liveness.show-components=always")) {

            HealthEndpoint health = app.getBean(HealthEndpoint.class);
            app.getBean(DatabaseHealthIndicator.class).setReachable(false);

            // Only meant to show more detail, but now a database outage fails liveness: restarts.
            assertThat(health.healthForPath("liveness").getStatus()).isEqualTo(Status.DOWN);
        }
    }

    @Test
    void withNoGroupConfiguration_livenessIsOnlyLivenessState() {
        try (ConfigurableApplicationContext app = new SpringApplicationBuilder(ProbesApplication.class).run(
                "--spring.config.name=none",
                "--server.port=0",
                "--management.endpoint.health.probes.enabled=true")) {

            app.getBean(DatabaseHealthIndicator.class).setReachable(false);

            assertThat(app.getBean(HealthEndpoint.class).healthForPath("liveness").getStatus()).isEqualTo(Status.UP);
            assertThat(app.getBean(HealthEndpoint.class).healthForPath("readiness").getStatus()).isEqualTo(Status.UP);
        }
    }
}

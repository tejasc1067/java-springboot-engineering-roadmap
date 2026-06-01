package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApplicationContextRunner spins up a small Spring context inside the test,
 * letting us assert "the context should have failed" without the failure
 * propagating up the test class lifecycle.
 *
 * This is the standard pattern for testing that validation rejects bad config.
 */
class InvalidRetryPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(App.class);

    @Test
    void zeroMaxAttemptsFailsValidationAtStartup() {
        runner.withPropertyValues(
                        "app.retry.max-attempts=0",
                        "app.retry.backoff=500ms",
                        "app.retry.notification.email=ops@example.com",
                        "app.retry.notification.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("maxAttempts")
                            .hasStackTraceContaining("must be greater than or equal to 1");
                });
    }

    @Test
    void blankNotificationEmailFailsValidationAtStartup() {
        runner.withPropertyValues(
                        "app.retry.max-attempts=3",
                        "app.retry.backoff=500ms",
                        "app.retry.notification.email=",
                        "app.retry.notification.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("notification.email")
                            .hasStackTraceContaining("must not be blank");
                });
    }

    @Test
    void validValuesProduceAHealthyContext() {
        runner.withPropertyValues(
                        "app.retry.max-attempts=5",
                        "app.retry.backoff=250ms",
                        "app.retry.notification.email=alice@example.com",
                        "app.retry.notification.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RetryProperties bound = context.getBean(RetryProperties.class);
                    assertThat(bound.maxAttempts()).isEqualTo(5);
                    assertThat(bound.notification().email()).isEqualTo("alice@example.com");
                    assertThat(bound.notification().enabled()).isFalse();
                });
    }
}

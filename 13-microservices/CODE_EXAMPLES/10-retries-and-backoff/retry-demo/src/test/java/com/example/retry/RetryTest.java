package com.example.retry;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry mechanics with Resilience4j: retry transient failures, back off between
 * attempts, give up after a cap, and never retry errors that won't get better.
 */
class RetryTest {

    @Test
    void withoutRetryTheFirstTransientFailurePropagates() {
        FlakyInventory inventory = new FlakyInventory(2); // fails twice, then would succeed

        assertThatThrownBy(inventory::getStock).isInstanceOf(TransientException.class);
        assertThat(inventory.callCount()).isEqualTo(1); // one attempt, one failure, done
    }

    @Test
    void withRetryItSucceedsAfterTransientFailures() {
        FlakyInventory inventory = new FlakyInventory(2);
        Retry retry = Retry.of("inventory", RetryConfig.custom()
                .maxAttempts(4)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(e -> e instanceof TransientException)
                .build());

        Supplier<String> withRetry = Retry.decorateSupplier(retry, inventory::getStock);
        String body = withRetry.get();

        assertThat(body).contains("SKU-BOOK");
        assertThat(inventory.callCount()).isEqualTo(3); // 2 failures + 1 success
    }

    @Test
    void retryGivesUpAfterMaxAttempts() {
        FlakyInventory alwaysFailing = new FlakyInventory(100); // never recovers within the test
        Retry retry = Retry.of("inventory", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(e -> e instanceof TransientException)
                .build());

        Supplier<String> withRetry = Retry.decorateSupplier(retry, alwaysFailing::getStock);

        assertThatThrownBy(withRetry::get).isInstanceOf(TransientException.class);
        assertThat(alwaysFailing.callCount()).isEqualTo(3); // tried the cap, then gave up
    }

    @Test
    void doesNotRetryErrorsThatWontGetBetter() {
        // A 4xx-style error (bad request) or a non-idempotent operation must NOT be retried.
        int[] calls = {0};
        Supplier<String> badRequest = () -> {
            calls[0]++;
            throw new IllegalArgumentException("400 bad request: unknown sku");
        };
        Retry retry = Retry.of("inventory", RetryConfig.custom()
                .maxAttempts(4)
                .waitDuration(Duration.ofMillis(10))
                .retryOnException(e -> e instanceof TransientException) // IAE is not transient
                .build());

        Supplier<String> decorated = Retry.decorateSupplier(retry, badRequest);

        assertThatThrownBy(decorated::get).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls[0]).isEqualTo(1); // one attempt only — retrying a 400 just fails 4x
    }

    @Test
    void exponentialBackoffWaitsLongerBetweenEachAttempt() {
        FlakyInventory inventory = new FlakyInventory(2); // succeeds on the 3rd attempt
        Retry retry = Retry.of("inventory", RetryConfig.custom()
                .maxAttempts(3)
                // 100ms before the 2nd attempt, 200ms before the 3rd (x2 each time)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(100), 2.0))
                .retryOnException(e -> e instanceof TransientException)
                .build());

        Supplier<String> withRetry = Retry.decorateSupplier(retry, inventory::getStock);

        long start = System.nanoTime();
        String body = withRetry.get();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(body).contains("SKU-BOOK");
        assertThat(inventory.callCount()).isEqualTo(3);
        // waited ~100ms + ~200ms between the three attempts
        assertThat(elapsedMs).isGreaterThanOrEqualTo(280L);
    }
}

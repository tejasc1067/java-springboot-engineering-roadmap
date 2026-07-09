package com.example.breaker;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

    private static final String FALLBACK = "DEGRADED: stock temporarily unavailable";

    private CircuitBreaker newBreaker() {
        return CircuitBreaker.of("inventory", CircuitBreakerConfig.custom()
                .slidingWindowType(SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(5)          // evaluate after 5 calls
                .failureRateThreshold(50f)         // open if >= 50% fail
                .waitDurationInOpenState(Duration.ofMillis(100))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build());
    }

    /** Drives 5 failing calls to trip the breaker, swallowing the exceptions. */
    private void trip(Supplier<String> decorated) {
        for (int i = 0; i < 5; i++) {
            try {
                decorated.get();
            } catch (RuntimeException ignored) {
                // expected while CLOSED: the downstream's own failure
            }
        }
    }

    @Test
    void withoutABreakerEveryCallHammersTheFailingDownstream() {
        ControllableInventory inventory = new ControllableInventory();
        inventory.setDown(true);

        for (int i = 0; i < 10; i++) {
            try {
                inventory.getStock();
            } catch (RuntimeException ignored) {
            }
        }

        // No breaker: all 10 calls hit the already-failing downstream (and in reality each
        // would also pay a full timeout). This is how an outage cascades into the caller.
        assertThat(inventory.callCount()).isEqualTo(10);
    }

    @Test
    void breakerOpensAndFailsFastLeavingTheDownstreamAlone() {
        ControllableInventory inventory = new ControllableInventory();
        inventory.setDown(true);
        CircuitBreaker breaker = newBreaker();
        Supplier<String> decorated = CircuitBreaker.decorateSupplier(breaker, inventory::getStock);

        trip(decorated);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsWhenOpened = inventory.callCount(); // 5

        // Now OPEN: further calls are short-circuited with CallNotPermittedException and
        // the downstream is NOT called — it gets breathing room to recover.
        assertThatThrownBy(decorated::get).isInstanceOf(CallNotPermittedException.class);
        assertThat(inventory.callCount()).isEqualTo(callsWhenOpened);
    }

    @Test
    void fallbackReturnsADegradedResponseInsteadOfAnError() {
        ControllableInventory inventory = new ControllableInventory();
        inventory.setDown(true);
        CircuitBreaker breaker = newBreaker();
        Supplier<String> decorated = CircuitBreaker.decorateSupplier(breaker, inventory::getStock);
        trip(decorated); // breaker now OPEN

        // Wrap the guarded call with a fallback: the caller gets a usable, degraded value
        // rather than an exception. (Only appropriate when a degraded answer is acceptable.)
        String result = guardedWithFallback(decorated);

        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    void recoversThroughHalfOpenBackToClosed() throws InterruptedException {
        ControllableInventory inventory = new ControllableInventory();
        CircuitBreaker breaker = newBreaker();
        Supplier<String> decorated = CircuitBreaker.decorateSupplier(breaker, inventory::getStock);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        inventory.setDown(true);
        trip(decorated);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        inventory.setDown(false);      // downstream recovers
        Thread.sleep(150);             // wait out waitDurationInOpenState (100ms)

        // The next calls are allowed as HALF_OPEN trials; both succeed, so the breaker closes.
        String first = decorated.get();
        String second = decorated.get();

        assertThat(first).contains("SKU-BOOK");
        assertThat(second).contains("SKU-BOOK");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private String guardedWithFallback(Supplier<String> decorated) {
        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            return FALLBACK;                 // circuit is open — fail fast to the fallback
        } catch (RuntimeException e) {
            return FALLBACK;                 // the call itself failed — degrade
        }
    }
}

package com.example.signals;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final ObservationRegistry observations;
    private final PricingClient pricing;

    public CheckoutService(ObservationRegistry observations, PricingClient pricing) {
        this.observations = observations;
        this.pricing = pricing;
    }

    /**
     * One observation produces two signals: a timer named "checkout" in the MeterRegistry
     * and a span named "checkout" in the tracer. The log line is the third signal.
     */
    public long checkout(String orderId, String sku) {
        return Observation.createNotStarted("checkout", observations).observe(() -> {
            long started = System.nanoTime();
            long total = pricing.priceCents(sku) + validate(orderId);
            long tookMillis = (System.nanoTime() - started) / 1_000_000;
            // Logged inside the observation, so Boot's log pattern also prints the trace id (topic 07).
            log.info("checkout finished orderId={} totalCents={} tookMs={}", orderId, total, tookMillis);
            return total;
        });
    }

    private long validate(String orderId) {
        return 0;   // fast local work
    }
}

package com.example.signals;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

/** Stands in for an HTTP call to pricing-service, which has become slow. */
@Component
public class PricingClient {

    private final ObservationRegistry observations;
    private volatile long delayMillis = 0;

    public PricingClient(ObservationRegistry observations) {
        this.observations = observations;
    }

    public long priceCents(String sku) {
        return Observation.createNotStarted("pricing.call", observations)
                .lowCardinalityKeyValue("downstream", "pricing-service")
                .observe(() -> {
                    pause(delayMillis);   // the slow downstream
                    return 1_999L;
                });
    }

    public void slowDownBy(long millis) {
        this.delayMillis = millis;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.example.probes;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.stereotype.Component;

/**
 * Warm-up work that must finish before the instance takes traffic: loading a cache, compiling templates,
 * opening connections. ApplicationRunners run after the web server has started but before Spring Boot
 * publishes ReadinessState.ACCEPTING_TRAFFIC, so readiness stays 503 until run() returns.
 */
@Component
public class CatalogWarmUp implements ApplicationRunner {

    private final ApplicationAvailability availability;
    private volatile ReadinessState readinessDuringWarmUp;
    private volatile LivenessState livenessDuringWarmUp;

    public CatalogWarmUp(ApplicationAvailability availability) {
        this.availability = availability;
    }

    @Override
    public void run(ApplicationArguments args) {
        readinessDuringWarmUp = availability.getReadinessState();
        livenessDuringWarmUp = availability.getLivenessState();
        // ... load the product catalogue into memory ...
    }

    public ReadinessState readinessDuringWarmUp() {
        return readinessDuringWarmUp;
    }

    public LivenessState livenessDuringWarmUp() {
        return livenessDuringWarmUp;
    }
}

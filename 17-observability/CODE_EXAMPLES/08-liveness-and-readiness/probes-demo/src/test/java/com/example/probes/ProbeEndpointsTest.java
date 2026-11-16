package com.example.probes;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/** What the two probe endpoints contain and how they react, with the correct configuration. */
@SpringBootTest
@AutoConfigureMockMvc
class ProbeEndpointsTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired DatabaseHealthIndicator database;

    @AfterEach
    void healthyAgain() {
        database.setReachable(true);
        AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
        AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test
    void liveness_containsOnlyTheAppsOwnState_readinessAlsoTheDatabase() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.livenessState.status").value("UP"))
                .andExpect(jsonPath("$.components", not(hasKey("database"))));

        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.readinessState.status").value("UP"))
                .andExpect(jsonPath("$.components.database.status").value("UP"));
    }

    @Test
    void databaseDown_readinessFails_livenessStillPasses() throws Exception {
        database.setReachable(false);

        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());       // nothing to restart: the JVM is fine
    }

    @Test
    void refusingTraffic_takesTheInstanceOutOfRotation_withoutTouchingLiveness() throws Exception {
        // e.g. while reloading a large cache, or when a circuit to a critical dependency is open
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);

        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void livenessBroken_isHowTheAppAsksToBeRestarted() throws Exception {
        // e.g. a component detected it is wedged in a state it can't recover from
        AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);

        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }
}

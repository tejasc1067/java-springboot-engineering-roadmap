package com.example.probes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

/** When Spring Boot itself changes the two states: during startup and during shutdown. */
class StartupAndShutdownTest {

    @Test
    void duringWarmUp_theAppIsLiveButNotReady_andBecomesReadyOnlyAfterTheRunnersFinish() {
        try (ConfigurableApplicationContext app = start(new CopyOnWriteArrayList<>())) {
            CatalogWarmUp warmUp = app.getBean(CatalogWarmUp.class);
            assertThat(warmUp.livenessDuringWarmUp()).isEqualTo(LivenessState.CORRECT);
            assertThat(warmUp.readinessDuringWarmUp()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);

            assertThat(app.getBean(ApplicationAvailability.class).getReadinessState())
                    .isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
        }
    }

    @Test
    void onShutdown_readinessFlipsToRefusingTraffic_beforeTheServerStops() {
        List<ReadinessState> readinessChanges = new CopyOnWriteArrayList<>();
        ConfigurableApplicationContext app = start(readinessChanges);
        assertThat(readinessChanges).containsExactly(ReadinessState.ACCEPTING_TRAFFIC);

        app.close();   // what SIGTERM triggers

        assertThat(readinessChanges)
                .containsExactly(ReadinessState.ACCEPTING_TRAFFIC, ReadinessState.REFUSING_TRAFFIC);
    }

    private static ConfigurableApplicationContext start(List<ReadinessState> readinessChanges) {
        ApplicationListener<AvailabilityChangeEvent<?>> recorder = event -> {
            if (event.getState() instanceof ReadinessState state) readinessChanges.add(state);
        };
        return new SpringApplicationBuilder(ProbesApplication.class)
                .listeners(recorder)
                .run("--server.port=0");
    }
}

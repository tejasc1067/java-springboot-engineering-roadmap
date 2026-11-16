package com.example.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.tracing.TracingProperties;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "management.tracing.sampling.probability=0.0")
class SamplingTest extends TracingTestBase {

    @Test
    void theDefaultProbability_isTenPercent() {
        assertThat(new TracingProperties().getSampling().getProbability()).isEqualTo(0.1f);
    }

    @Test
    void notSampled_stillHasATraceId_butNoSpansAreExported() {
        for (int i = 0; i < 20; i++) {
            Map<?, ?> result = http.get().uri("/stock/A1").retrieve().body(Map.class);
            assertThat((String) result.get("traceId")).hasSize(32);
        }

        assertThat(spans.finished()).isEmpty();
    }
}

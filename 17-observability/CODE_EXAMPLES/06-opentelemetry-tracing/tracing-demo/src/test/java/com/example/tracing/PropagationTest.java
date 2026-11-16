package com.example.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PropagationTest extends TracingTestBase {

    @Test
    void restClientFromBootsBuilder_sendsTraceparent_soTheDownstreamContinuesTheTrace() {
        Map<?, ?> result = http.get().uri("/checkout?sku=A1&client=traced").retrieve().body(Map.class);
        Map<?, ?> downstream = (Map<?, ?>) result.get("downstream");

        String traceId = (String) result.get("traceId");
        assertThat(downstream.get("traceId")).isEqualTo(traceId);
        // version - trace id (32 hex) - parent span id (16 hex) - flags (01 = sampled)
        assertThat((String) downstream.get("traceparent")).matches("00-" + traceId + "-[0-9a-f]{16}-01");

        // checkout (server) -> http get (client) -> stock (server) -> check-stock: four spans, one trace
        assertThat(spans.finished()).hasSize(4).allMatch(s -> s.getTraceId().equals(traceId));
    }

    @Test
    void restClientCreate_sendsNoTraceparent_soTheDownstreamStartsANewTrace() {
        Map<?, ?> result = http.get().uri("/checkout?sku=A1&client=untraced").retrieve().body(Map.class);
        Map<?, ?> downstream = (Map<?, ?>) result.get("downstream");

        assertThat(downstream.get("traceparent")).isEqualTo("null");
        assertThat(downstream.get("traceId")).isNotEqualTo(result.get("traceId"));
        // Two unrelated traces, and no client span: the outgoing call is invisible.
        assertThat(spans.finished()).extracting(s -> s.getTraceId()).containsOnly(
                (String) result.get("traceId"), (String) downstream.get("traceId"));
        assertThat(spans.names()).doesNotContain("http get");
    }

    @Test
    void anIncomingTraceparent_isContinued() {
        String upstreamTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";

        Map<?, ?> result = http.get().uri("/stock/A1")
                .header("traceparent", "00-" + upstreamTraceId + "-00f067aa0ba902b7-01")
                .retrieve().body(Map.class);

        assertThat(result.get("traceId")).isEqualTo(upstreamTraceId);
        assertThat(spans.named("http get /stock/{sku}").getParentSpanId()).isEqualTo("00f067aa0ba902b7");
    }

    @Test
    void anUpstreamNotSampledDecision_isRespected_evenAtProbabilityOne() {
        String upstreamTraceId = "4bf92f3577b34da6a3ce929d0e0e4737";

        Map<?, ?> result = http.get().uri("/stock/A1")
                .header("traceparent", "00-" + upstreamTraceId + "-00f067aa0ba902b7-00")   // flags 00: not sampled
                .retrieve().body(Map.class);

        assertThat(result.get("traceId")).isEqualTo(upstreamTraceId);   // the trace id still flows
        assertThat(spans.finished()).isEmpty();                         // but nothing is exported
    }
}

package com.example.logcorrelation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExemplarTest extends LogTestBase {

    private static final String OPEN_METRICS = "application/openmetrics-text; version=1.0.0; charset=utf-8";

    @Test
    void openMetrics_attachesATraceIdToTheLatencyBuckets_thePlainTextFormatDoesNot() {
        Reply reply = get("/orders/60");

        String openMetrics = http.get().uri("/actuator/prometheus").header("Accept", OPEN_METRICS)
                .retrieve().body(String.class);
        // http_server_requests_seconds_bucket{...,status="200",uri="/orders/{id}",le="0.033554431"} 1 # {span_id="...",trace_id="..."} 0.0331 1794832601.226
        assertThat(openMetrics.lines()
                .filter(l -> l.startsWith("http_server_requests_seconds_bucket") && l.contains("uri=\"/orders/{id}\"")))
                .anyMatch(l -> l.contains("# {span_id=\"") && l.contains("trace_id=\"" + reply.traceIdHeader() + "\""));

        String plainText = http.get().uri("/actuator/prometheus").retrieve().body(String.class);
        assertThat(plainText).contains("http_server_requests_seconds_bucket").doesNotContain("trace_id");
    }
}

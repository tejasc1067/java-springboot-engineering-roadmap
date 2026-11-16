package com.example.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;

class SpanTest extends TracingTestBase {

    @Test
    void oneRequest_isOneTrace_aServerSpanWithAChildSpanInside() {
        http.get().uri("/stock/A1").retrieve().toBodilessEntity();

        SpanData server = spans.named("http get /stock/{sku}");
        SpanData child = spans.named("check-stock");

        assertThat(server.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(server.getParentSpanContext().isValid()).isFalse();          // no parent: the root of the trace
        assertThat(child.getTraceId()).isEqualTo(server.getTraceId());          // same trace
        assertThat(child.getParentSpanId()).isEqualTo(server.getSpanId());      // nested inside the server span
        assertThat(child.getEndEpochNanos()).isLessThanOrEqualTo(server.getEndEpochNanos());
    }

    @Test
    void spring_tagsTheServerSpan_withMethodRouteAndStatus() {
        http.get().uri("/stock/A1").retrieve().toBodilessEntity();

        SpanData server = spans.named("http get /stock/{sku}");
        assertThat(server.getAttributes().get(AttributeKey.stringKey("method"))).isEqualTo("GET");
        assertThat(server.getAttributes().get(AttributeKey.stringKey("uri"))).isEqualTo("/stock/{sku}");
        assertThat(server.getAttributes().get(AttributeKey.stringKey("http.url"))).isEqualTo("/stock/A1");
        assertThat(server.getAttributes().get(AttributeKey.stringKey("status"))).isEqualTo("200");
    }

    @Test
    void aCustomSpan_carriesYourAttributesAndEvents() {
        http.get().uri("/stock/A1").retrieve().toBodilessEntity();

        SpanData child = spans.named("check-stock");
        assertThat(child.getAttributes().get(AttributeKey.stringKey("warehouse"))).isEqualTo("eu");
        assertThat(child.getAttributes().get(AttributeKey.stringKey("sku"))).isEqualTo("A1");
        assertThat(child.getEvents()).extracting(EventData::getName).containsExactly("cache-miss");
    }

    @Test
    void anException_marksTheSpansAsErrors_andRecordsTheException() {
        try {
            http.get().uri("/stock/unknown-1").retrieve().toBodilessEntity();
        } catch (HttpServerErrorException expected) {
            // 500
        }

        SpanData child = spans.named("check-stock");
        assertThat(child.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(child.getStatus().getDescription()).isEqualTo("no such sku: unknown-1");
        assertThat(child.getEvents()).extracting(EventData::getName).contains("exception");

        SpanData server = spans.named("http get /stock/{sku}");
        assertThat(server.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(server.getAttributes().get(AttributeKey.stringKey("status"))).isEqualTo("500");
    }

    @Test
    void observedAnnotation_createsASpanWithoutAnyTracingCode() {
        http.get().uri("/price/A1").retrieve().toBodilessEntity();

        SpanData span = spans.named("look-up-price");
        assertThat(span.getParentSpanId()).isEqualTo(spans.named("http get /price/{sku}").getSpanId());
        assertThat(span.getAttributes().get(AttributeKey.stringKey("method"))).isEqualTo("price");
    }
}

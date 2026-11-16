package com.example.signals;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One slow downstream call, diagnosed three ways. Each signal answers a different question.
 */
@SpringBootTest
@Import(InMemorySpans.class)
@ExtendWith(OutputCaptureExtension.class)
class ThreeSignalsTest {

    @Autowired CheckoutService checkout;
    @Autowired PricingClient pricing;
    @Autowired MeterRegistry meters;
    @Autowired InMemorySpanExporter spans;
    @Autowired SdkTracerProvider tracerProvider;

    @BeforeEach
    void reset() {
        meters.clear();
        spans.reset();
        pricing.slowDownBy(0);
    }

    @Test
    void log_tellsYouThatThisOneRequestWasSlow_butNotWhy(CapturedOutput output) {
        pricing.slowDownBy(200);
        checkout.checkout("order-42", "sku-1");

        Matcher line = Pattern.compile("checkout finished orderId=order-42 totalCents=1999 tookMs=(\\d+)")
                .matcher(output.getOut());
        assertThat(line.find()).isTrue();
        assertThat(Long.parseLong(line.group(1))).isGreaterThanOrEqualTo(200);
        // Nothing in that line says the time went to pricing-service.
    }

    @Test
    void metrics_tellYouHowManyAndHowSlow_acrossAllRequests_butNotWhichOne() {
        for (int i = 1; i <= 4; i++) checkout.checkout("order-" + i, "sku-1");
        pricing.slowDownBy(200);
        checkout.checkout("order-5", "sku-1");

        Timer checkoutTimer = meters.get("checkout").timer();
        Timer pricingTimer = meters.get("pricing.call").tag("downstream", "pricing-service").timer();

        assertThat(checkoutTimer.count()).isEqualTo(5);
        assertThat(checkoutTimer.max(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(200);
        assertThat(checkoutTimer.mean(TimeUnit.MILLISECONDS)).isLessThan(checkoutTimer.max(TimeUnit.MILLISECONDS) / 2);
        // The downstream timer is just as slow, so the time is spent there...
        assertThat(pricingTimer.max(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(200);
        // ...but no metric says which order it was: order ids are never tags (topic 03).
        assertThat(meters.get("checkout").timer().getId().getTags())
                .noneMatch(tag -> tag.getValue().startsWith("order-"));
    }

    @Test
    void trace_showsWhereTheTimeWent_insideThisOneRequest() {
        pricing.slowDownBy(200);
        checkout.checkout("order-42", "sku-1");

        List<SpanData> finished = InMemorySpans.finished(tracerProvider, spans);
        SpanData parent = only(finished, "checkout");
        SpanData child = only(finished, "pricing.call");

        assertThat(child.getTraceId()).isEqualTo(parent.getTraceId());
        assertThat(child.getParentSpanId()).isEqualTo(parent.getSpanId());
        long parentMs = millis(parent);
        long childMs = millis(child);
        assertThat(childMs).isGreaterThanOrEqualTo(200);
        assertThat(childMs).isGreaterThanOrEqualTo(parentMs * 9 / 10);   // 90%+ of the request is the pricing call
    }

    private static SpanData only(List<SpanData> spans, String name) {
        return spans.stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static long millis(SpanData span) {
        return (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000;
    }
}

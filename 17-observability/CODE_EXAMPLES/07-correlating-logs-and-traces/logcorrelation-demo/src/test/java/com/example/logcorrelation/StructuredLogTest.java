package com.example.logcorrelation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "logging.structured.format.console=logstash")
class StructuredLogTest extends LogTestBase {

    @Test
    void jsonLogs_carryTraceIdAndSpanId_asSearchableFields(CapturedOutput output) throws Exception {
        Reply reply = get("/orders/50");

        Map<String, Object> line = json(lineContaining(output, "\"loading order 50\""));
        assertThat(line.get("traceId")).isEqualTo(reply.traceIdHeader());
        assertThat((String) line.get("spanId")).hasSize(16);
        assertThat(line).containsEntry("orderId", "50").containsEntry("customerId", "c-50");
    }

    @Test
    void onAnotherThread_theTraceAndBaggageArrive_butYourOwnMdcKeysDoNot(CapturedOutput output) throws Exception {
        Reply reply = get("/orders/51");

        Map<String, Object> traced = json(lineContaining(output, "emailing receipt (traced pool)"));
        assertThat(traced.get("traceId")).isEqualTo(reply.traceIdHeader());
        assertThat(traced).containsEntry("customerId", "c-51")   // baggage is part of the trace context
                .doesNotContainKey("orderId");                    // MDC.put on the request thread stays there

        Map<String, Object> plain = json(lineContaining(output, "emailing receipt (plain pool)"));
        assertThat(plain).doesNotContainKeys("traceId", "customerId", "orderId");
    }
}

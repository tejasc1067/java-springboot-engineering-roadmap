package com.example.logcorrelation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("catch-all")
class CatchAllErrorTest extends LogTestBase {

    @Test
    void aCatchAllHandler_logsTheErrorWithTheTraceId_andReturnsIt(CapturedOutput output) throws Exception {
        Reply reply = get("/orders/crash-2");

        assertThat(reply.status()).isEqualTo(500);
        assertThat(json(reply.body()).get("traceId")).isEqualTo(reply.traceIdHeader());
        assertThat(reply.body()).doesNotContain("database unavailable");   // internals stay in the log

        String errorLine = lineContaining(output, "request failed");
        assertThat(errorLine).contains("ERROR").contains("[" + reply.traceIdHeader() + "-");
        assertThat(output.getOut()).doesNotContain("Servlet.service()");
    }
}

package com.example.logcorrelation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

class LogCorrelationTest extends LogTestBase {

    @Test
    void everyLogLineInTheRequest_carriesTheTraceIdAndSpanId(CapturedOutput output) {
        Reply reply = get("/orders/42");

        String traceId = reply.traceIdHeader();
        assertThat(traceId).hasSize(32);
        // Boot's default console pattern: ... [app name] [thread] [traceId-spanId] logger : message
        assertThat(lineContaining(output, "loading order 42"))
                .containsPattern("\\[orders\\] \\[.+\\] \\[" + traceId + "-[0-9a-f]{16}\\] ");
    }

    @Test
    void aPlainThreadPool_logsWithAnEmptyTraceId(CapturedOutput output) {
        get("/orders/43");

        assertThat(lineContaining(output, "emailing receipt (plain pool)")).containsPattern("\\[ +\\] ");
    }

    @Test
    void aPoolWithContextPropagatingTaskDecorator_logsTheRequestsTraceId(CapturedOutput output) {
        Reply reply = get("/orders/44");

        assertThat(lineContaining(output, "emailing receipt (traced pool)")).contains("[" + reply.traceIdHeader() + "-");
    }

    @Test
    void anUnhandledException_theStackTraceLineHasNoTraceId_andNeitherDoesTheResponse(CapturedOutput output) {
        Reply reply = get("/orders/crash-1");

        assertThat(reply.status()).isEqualTo(500);
        assertThat(reply.traceIdHeader()).hasSize(32);                     // the request did have a trace
        assertThat(lineContaining(output, "loading order crash-1")).contains(reply.traceIdHeader());

        // The one line you need during an incident is logged by Tomcat after the request's span has closed.
        String errorLine = lineContaining(output, "Servlet.service()");
        assertThat(errorLine).contains("database unavailable").doesNotContain(reply.traceIdHeader());
        assertThat(reply.body()).doesNotContain("traceId");                // Boot's default error body
    }
}

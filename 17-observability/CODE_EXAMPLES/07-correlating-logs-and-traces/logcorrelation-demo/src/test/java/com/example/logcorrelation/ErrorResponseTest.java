package com.example.logcorrelation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;

class ErrorResponseTest extends LogTestBase {

    @Test
    void aHandledError_returnsTheTraceIdInTheProblemDetail(CapturedOutput output) throws Exception {
        Reply reply = get("/orders/missing-7");

        assertThat(reply.status()).isEqualTo(404);
        Object traceId = json(reply.body()).get("traceId");
        assertThat(traceId).isEqualTo(reply.traceIdHeader());
        // Support pastes the id into the log search and lands on this request's lines.
        assertThat(lineContaining(output, "loading order missing-7")).contains("[" + traceId + "-");
    }
}

package com.example.logging;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/** The MDC belongs to a thread. @Async work runs on another thread, so by default it has none of it. */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class MdcAsyncTest {

    @Autowired ReceiptService receipts;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void asyncWork_losesTheCallersMdc(CapturedOutput output) {
        MDC.put("requestId", "req-7");
        receipts.sendReceipt("order-1").join();

        JsonNode event = JsonLines.withMessage(output, "receipt sent for order-1");
        assertThat(event.at("/process/thread/name").asText()).startsWith("task-");
        assertThat(event.has("requestId")).isFalse();
    }
}

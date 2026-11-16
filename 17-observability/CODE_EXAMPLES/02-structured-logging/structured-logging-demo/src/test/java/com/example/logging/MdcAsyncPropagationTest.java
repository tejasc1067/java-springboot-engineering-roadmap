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
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mdc-propagation")
@ExtendWith(OutputCaptureExtension.class)
class MdcAsyncPropagationTest {

    @Autowired ReceiptService receipts;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void withATaskDecorator_asyncWorkKeepsTheCallersMdc(CapturedOutput output) {
        MDC.put("requestId", "req-7");
        receipts.sendReceipt("order-1").join();

        JsonNode event = JsonLines.withMessage(output, "receipt sent for order-1");
        assertThat(event.at("/process/thread/name").asText()).startsWith("task-");
        assertThat(event.get("requestId").asText()).isEqualTo("req-7");
    }

    @Test
    void theWorkerThreadDoesNotKeepItAfterwards(CapturedOutput output) {
        MDC.put("requestId", "req-7");
        receipts.sendReceipt("order-1").join();
        MDC.clear();
        receipts.sendReceipt("order-2").join();

        assertThat(JsonLines.withMessage(output, "receipt sent for order-2").has("requestId")).isFalse();
    }
}

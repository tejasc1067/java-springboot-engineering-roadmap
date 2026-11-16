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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class JsonLogFormatTest {

    @Autowired OrderService orders;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void eachEventIsOneJsonObject_withFieldsAQueryCanUse(CapturedOutput output) {
        orders.placeOrder("order-1", 1999);

        JsonNode event = JsonLines.withMessage(output, "order placed");
        assertThat(event.at("/log/level").asText()).isEqualTo("INFO");
        assertThat(event.at("/service/name").asText()).isEqualTo("order-service");
        assertThat(event.get("orderId").asText()).isEqualTo("order-1");
        assertThat(event.get("totalCents").isNumber()).isTrue();   // a number, so "totalCents > 10000" works
        assertThat(event.get("totalCents").asLong()).isEqualTo(1999);
    }

    @Test
    void mdcEntries_becomeFieldsOnEveryEvent(CapturedOutput output) {
        MDC.put("userId", "ann");
        orders.placeOrder("order-1", 1999);
        orders.failPayment("order-2");

        assertThat(JsonLines.withMessage(output, "order placed").get("userId").asText()).isEqualTo("ann");
        assertThat(JsonLines.withMessage(output, "payment failed for order order-2").get("userId").asText()).isEqualTo("ann");
    }

    @Test
    void anErrorWithItsStackTrace_isStillOneLine(CapturedOutput output) {
        orders.failPayment("order-2");

        List<String> linesMentioningIt = output.getOut().lines().filter(l -> l.contains("card declined")).toList();
        assertThat(linesMentioningIt).hasSize(1);

        JsonNode event = JsonLines.withMessage(output, "payment failed for order order-2");
        assertThat(event.at("/log/level").asText()).isEqualTo("ERROR");
        assertThat(event.at("/error/type").asText()).isEqualTo("java.lang.IllegalStateException");
        assertThat(event.at("/error/stack_trace").asText()).contains("\n\tat com.example.logging.OrderService.failPayment");
    }
}

package com.example.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same two events as OrderService logs, written with a classic text pattern (close to Spring Boot's
 * default). A private Logback context keeps it separate from the JSON logging the rest of the tests use.
 */
class PlainTextLogTest {

    private final PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private Logger log;

    @BeforeEach
    void textLogger() {
        LoggerContext context = new LoggerContext();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd'T'HH:mm:ss.SSS} %5p [%t] %logger : %m%n");
        encoder.start();
        events.setContext(context);
        events.start();
        log = context.getLogger("com.example.logging.OrderService");
        log.addAppender(events);
    }

    @Test
    void anErrorIsSpreadOverManyLines_andOnlyTheFirstOneSaysWhichOrder() {
        log.error("payment failed for order {}", "order-2", new IllegalStateException("card declined"));

        List<String> lines = text().lines().toList();
        assertThat(lines.stream().filter(l -> l.startsWith("\tat "))).hasSizeGreaterThan(10);
        // grep "order-2" finds one line; the exception and its stack trace are on other lines.
        assertThat(lines.stream().filter(l -> l.contains("order-2"))).hasSize(1);
        assertThat(lines.stream().filter(l -> l.contains("order-2") && l.contains("card declined"))).isEmpty();
    }

    @Test
    void keyValuePairs_areNotInATextPattern() {
        log.atInfo().setMessage("order placed").addKeyValue("orderId", "order-1").log();

        assertThat(text()).contains("order placed").doesNotContain("order-1");
    }

    private String text() {
        return events.list.stream()
                .map(event -> new String(encoder.encode(event), StandardCharsets.UTF_8))
                .collect(Collectors.joining());
    }
}

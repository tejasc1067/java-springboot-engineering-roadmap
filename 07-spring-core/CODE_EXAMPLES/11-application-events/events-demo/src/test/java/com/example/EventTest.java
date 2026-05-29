package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class EventTest {

    @Test
    void placingAnOrderTriggersAllListeners() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orderService    = ctx.getBean(OrderService.class);
            AuditLogger audit            = ctx.getBean(AuditLogger.class);
            NotificationSender notifier  = ctx.getBean(NotificationSender.class);

            String id = orderService.place("ada@example.com");

            // Both listeners received the same event without OrderService knowing they existed.
            assertThat(audit.entries())
                    .hasSize(1)
                    .first().asString().contains(id).contains("ada@example.com");
            assertThat(notifier.sent())
                    .hasSize(1)
                    .first().asString().contains(id).contains("ada@example.com");
        }
    }

    @Test
    void multiplePublishesFanOutToEveryListenerEachTime() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orderService    = ctx.getBean(OrderService.class);
            AuditLogger audit            = ctx.getBean(AuditLogger.class);
            NotificationSender notifier  = ctx.getBean(NotificationSender.class);

            orderService.place("ada@example.com");
            orderService.place("ben@example.com");
            orderService.place("cara@example.com");

            assertThat(audit.entries()).hasSize(3);
            assertThat(notifier.sent()).hasSize(3);
        }
    }
}

package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    @Test
    void containerBuildsAllExpectedBeans() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            assertThat(ctx.getBean(InventoryService.class)).isNotNull();
            assertThat(ctx.getBean(PriceCalculator.class)).isNotNull();
            assertThat(ctx.getBean(EmailNotifier.class)).isInstanceOf(ConsoleEmailNotifier.class);
            assertThat(ctx.getBean(OrderRepository.class)).isInstanceOf(InMemoryOrderRepository.class);
            assertThat(ctx.getBean(OrderService.class)).isNotNull();
        }
    }

    @Test
    void beansAreSingletonsByDefault() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService a = ctx.getBean(OrderService.class);
            OrderService b = ctx.getBean(OrderService.class);

            assertThat(a).isSameAs(b);                         // identity, not just equality
        }
    }

    @Test
    void beanLookupByNameMatchesMethodName() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService byType = ctx.getBean(OrderService.class);
            OrderService byName = ctx.getBean("orderService", OrderService.class);

            assertThat(byName).isSameAs(byType);
        }
    }

    @Test
    void springWiredOrderServiceWorksEndToEnd() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orderService = ctx.getBean(OrderService.class);
            OrderRepository repo      = ctx.getBean(OrderRepository.class);

            Order order = orderService.placeOrder("ada@example.com", List.of("BOOK-A", "PEN-B"));

            assertThat(order.total()).isEqualByComparingTo(new BigDecimal("22.00"));
            assertThat(repo.findById(order.id())).isPresent();
            assertThat(repo.count()).isEqualTo(1);
        }
    }
}

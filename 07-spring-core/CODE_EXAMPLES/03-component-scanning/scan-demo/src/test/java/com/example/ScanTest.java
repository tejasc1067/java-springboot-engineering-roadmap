package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanTest {

    @Test
    void scanPicksUpEveryStereotype() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            assertThat(ctx.getBean(InventoryService.class)).isNotNull();
            assertThat(ctx.getBean(PriceCalculator.class)).isNotNull();
            assertThat(ctx.getBean(EmailNotifier.class)).isInstanceOf(ConsoleEmailNotifier.class);
            assertThat(ctx.getBean(OrderRepository.class)).isInstanceOf(InMemoryOrderRepository.class);
            assertThat(ctx.getBean(OrderService.class)).isNotNull();
        }
    }

    @Test
    void beanNameDefaultsToLowercasedClassName() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            // The scanner names beans by class name with the first letter lowercased.
            OrderService byName = ctx.getBean("orderService", OrderService.class);
            assertThat(byName).isSameAs(ctx.getBean(OrderService.class));
        }
    }

    @Test
    void valueObjectsAreNotBeans() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            // Order has no @Component annotation; the container should not know about it.
            assertThat(ctx.getBeansOfType(Order.class)).isEmpty();
        }
    }

    @Test
    void scannedOrderServiceWorksEndToEnd() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orderService = ctx.getBean(OrderService.class);
            OrderRepository repo      = ctx.getBean(OrderRepository.class);

            Order order = orderService.placeOrder("ada@example.com", List.of("BOOK-A", "PEN-B"));

            assertThat(order.total()).isEqualByComparingTo(new BigDecimal("22.00"));
            assertThat(repo.findById(order.id())).isPresent();
        }
    }
}

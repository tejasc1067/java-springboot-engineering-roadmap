package com.example;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest {

    // Notice: this is the *same* five-line wiring as Main, copied. Every test class that
    // exercises OrderService will copy it. That duplication is what topic 02 starts removing.
    private final InventoryService inventory       = new InventoryService();
    private final PriceCalculator priceCalculator  = new PriceCalculator(0.10);
    private final EmailNotifier notifier           = new ConsoleEmailNotifier();
    private final OrderRepository orderRepository  = new InMemoryOrderRepository();
    private final OrderService orderService        = new OrderService(
            inventory, priceCalculator, notifier, orderRepository);

    @Test
    void placesOrderWhenAllItemsInStock() {
        Order order = orderService.placeOrder("ada@example.com", List.of("BOOK-A", "PEN-B"));

        assertThat(order.customer()).isEqualTo("ada@example.com");
        assertThat(order.total()).isEqualByComparingTo(new BigDecimal("22.00"));
        assertThat(orderRepository.findById(order.id())).isPresent();
    }

    @Test
    void refusesOrderWhenAnyItemNotInStock() {
        assertThatThrownBy(() -> orderService.placeOrder("ada@example.com", List.of("BOOK-A", "GHOST-Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GHOST-Z");
    }
}

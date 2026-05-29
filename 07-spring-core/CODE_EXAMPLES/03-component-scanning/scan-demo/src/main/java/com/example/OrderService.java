package com.example;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final InventoryService inventory;
    private final PriceCalculator priceCalculator;
    private final EmailNotifier notifier;
    private final OrderRepository orderRepository;

    // Single constructor. Spring uses it automatically. No @Autowired needed.
    public OrderService(InventoryService inventory,
                        PriceCalculator priceCalculator,
                        EmailNotifier notifier,
                        OrderRepository orderRepository) {
        this.inventory = inventory;
        this.priceCalculator = priceCalculator;
        this.notifier = notifier;
        this.orderRepository = orderRepository;
    }

    public Order placeOrder(String customer, List<String> items) {
        for (String item : items) {
            if (!inventory.inStock(item)) {
                throw new IllegalArgumentException("Item not in stock: " + item);
            }
        }
        BigDecimal total = priceCalculator.total(items);
        Order order = new Order(UUID.randomUUID().toString(), customer, items, total);
        orderRepository.save(order);
        notifier.send(customer, "Order " + order.id() + " placed. Total: " + total);
        return order;
    }
}

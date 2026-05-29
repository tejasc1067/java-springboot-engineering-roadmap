package com.example;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        // ---- the wiring. five constructors, in dependency order. ----
        InventoryService inventory     = new InventoryService();
        PriceCalculator priceCalculator = new PriceCalculator(0.10);
        EmailNotifier notifier          = new ConsoleEmailNotifier();
        OrderRepository orderRepository = new InMemoryOrderRepository();
        OrderService orderService       = new OrderService(
                inventory, priceCalculator, notifier, orderRepository);
        // ---- end of wiring. now use it. ----

        Order placed = orderService.placeOrder("ada@example.com", List.of("BOOK-A", "PEN-B"));
        System.out.println("Placed order id=" + placed.id() + ", total=" + placed.total());
        System.out.println("Repository now holds " + orderRepository.count() + " order(s).");

        // The wiring above is five lines for five classes. That ratio holds. For a real
        // app with hundreds of services it becomes hundreds of lines, all in one place,
        // all needing to be edited every time a constructor changes. That is the cost
        // Spring removes -- and topic 02 is where the removal starts.
    }
}

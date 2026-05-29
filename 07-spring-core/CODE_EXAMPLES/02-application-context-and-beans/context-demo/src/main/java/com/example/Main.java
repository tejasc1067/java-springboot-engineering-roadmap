package com.example;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orderService = context.getBean(OrderService.class);

            Order placed = orderService.placeOrder("ada@example.com", List.of("BOOK-A", "PEN-B"));
            System.out.println("Placed order id=" + placed.id() + ", total=" + placed.total());

            OrderRepository repo = context.getBean(OrderRepository.class);
            System.out.println("Repository now holds " + repo.count() + " order(s).");
        }
        // try-with-resources closed the context. Any @PreDestroy hooks (topic 07) ran here.
    }
}

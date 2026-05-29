package com.example;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orderService = context.getBean(OrderService.class);

            String id1 = orderService.place("ada@example.com");
            String id2 = orderService.place("ben@example.com");

            System.out.println("placed " + id1 + " and " + id2 + "; check the AUDIT/NOTIFY lines above.");
        }
    }
}

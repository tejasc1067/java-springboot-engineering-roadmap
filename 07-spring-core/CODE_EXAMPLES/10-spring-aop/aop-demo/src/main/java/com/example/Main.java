package com.example;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orderService = context.getBean(OrderService.class);
            UserService userService   = context.getBean(UserService.class);

            orderService.place("ada@example.com");
            userService.lookup("42");
            orderService.place("ben@example.com");

            TimingAspect aspect = context.getBean(TimingAspect.class);
            System.out.println("-- aspect recorded " + aspect.events().size() + " event(s) --");
        }
    }
}

package com.example;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            Mailer mailer = context.getBean(Mailer.class);

            System.out.println("active profile picked channel: " + mailer.channel());
            mailer.send("ada@example.com", "hello");
        }
    }
}

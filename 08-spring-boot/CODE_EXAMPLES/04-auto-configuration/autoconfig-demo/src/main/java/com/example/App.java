package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(App.class, args);

        Greeter greeter = context.getBean(Greeter.class);
        System.out.println(greeter.greet("Spring Boot"));
        System.out.println("Wired implementation: " + greeter.getClass().getSimpleName());
    }
}

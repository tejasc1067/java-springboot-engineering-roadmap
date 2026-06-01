package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(App.class, args);

        Greeter greeter = context.getBean(Greeter.class);
        Environment env = context.getEnvironment();

        String[] active = env.getActiveProfiles();
        System.out.println("Active profiles: " + (active.length == 0 ? "(none)" : Arrays.toString(active)));
        System.out.println("Greeting: " + greeter.greeting());
    }
}

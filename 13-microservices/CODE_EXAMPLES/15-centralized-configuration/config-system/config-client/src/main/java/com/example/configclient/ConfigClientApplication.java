package com.example.configclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A service whose configuration lives on the config server, not in its own file. The
 * `inventory.greeting` value below is served centrally; this app has no such property
 * locally. @RefreshScope means that after the config changes on the server, a POST to
 * /actuator/refresh rebuilds this bean with the new value — no restart needed.
 */
@SpringBootApplication
public class ConfigClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigClientApplication.class, args);
    }

    @RestController
    @RefreshScope
    static class GreetingController {

        private final String greeting;
        private final int lowStockThreshold;

        GreetingController(@Value("${inventory.greeting:NOT CONFIGURED (config server unreachable)}") String greeting,
                           @Value("${inventory.low-stock-threshold:0}") int lowStockThreshold) {
            this.greeting = greeting;
            this.lowStockThreshold = lowStockThreshold;
        }

        @GetMapping("/api/greeting")
        public String greeting() {
            return greeting + " | lowStockThreshold=" + lowStockThreshold;
        }
    }
}

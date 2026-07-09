package com.example.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * The config server: one place that holds configuration for every service. Services
 * ask it for their config by name at startup. @EnableConfigServer turns this Boot app
 * into that server; it serves the files under resources/config/ (native profile).
 *
 * Try it once running: http://localhost:8888/inventory-service/default
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}

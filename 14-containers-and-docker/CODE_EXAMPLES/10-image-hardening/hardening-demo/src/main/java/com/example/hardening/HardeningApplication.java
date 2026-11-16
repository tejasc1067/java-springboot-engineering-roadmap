package com.example.hardening;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class HardeningApplication {

    public static void main(String[] args) {
        SpringApplication.run(HardeningApplication.class, args);
    }

    @GetMapping("/hello")
    public String hello() {
        return "hello from user " + System.getProperty("user.name");
    }
}

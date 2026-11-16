package com.example.layers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class LayersApplication {

    // demo.sh edits this line to simulate "I changed one line of my code".
    static final String GREETING = "hello v1";

    @GetMapping("/hello")
    String hello() { return GREETING; }

    public static void main(String[] args) {
        SpringApplication.run(LayersApplication.class, args);
    }
}

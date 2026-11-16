package com.example.dockerfile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class DockerfileDemoApplication {

    @GetMapping("/hello")
    String hello() {
        return "Hello from a container";
    }

    public static void main(String[] args) {
        SpringApplication.run(DockerfileDemoApplication.class, args);
    }
}

package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@SpringBootApplication
@EnableJpaAuditing
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    // In a real app this would resolve the current user from SecurityContextHolder.
    @org.springframework.context.annotation.Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of("test-user");
    }
}

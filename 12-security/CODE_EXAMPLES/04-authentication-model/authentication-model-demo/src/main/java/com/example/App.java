package com.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    // Seed two users at startup. Their passwords are stored ENCODED (BCrypt), never as plaintext — we run
    // the raw password through the SAME PasswordEncoder bean the login path will later use to verify it.
    @Bean
    CommandLineRunner seedUsers(AppUserRepository users, PasswordEncoder encoder) {
        return args -> {
            if (users.findByUsername("alice").isEmpty()) {
                users.save(new AppUser("alice", encoder.encode("password"), "ROLE_USER"));
                users.save(new AppUser("bob", encoder.encode("s3cret"), "ROLE_USER"));
            }
        };
    }
}

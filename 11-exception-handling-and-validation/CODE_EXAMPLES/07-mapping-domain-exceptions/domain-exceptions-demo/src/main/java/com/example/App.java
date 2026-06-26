package com.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    // Seed one book (id 1, ISBN 9780201616224) so the GET-404 and duplicate-409 demos have something to hit.
    @Bean
    CommandLineRunner seed(BookRepository repository) {
        return args -> repository.save(
                new Book("The Pragmatic Programmer", "Hunt & Thomas", "9780201616224"));
    }
}

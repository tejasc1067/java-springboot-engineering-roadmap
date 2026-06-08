package com.example;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class BookService {

    private static final List<Book> SEED = List.of(
            new Book(1L, "Effective Java", "Bloch"),
            new Book(2L, "Clean Code", "Martin"));

    public List<Book> findAll() {
        return SEED;
    }

    public Optional<Book> findById(Long id) {
        return SEED.stream().filter(b -> b.id().equals(id)).findFirst();
    }
}

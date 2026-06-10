package com.example;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthorService {

    private final AuthorRepository repository;

    public AuthorService(AuthorRepository repository) {
        this.repository = repository;
    }

    // Returns a managed entity with a lazy 'books' collection.
    // The transaction ends when this method returns, so the caller gets a detached author.
    @Transactional(readOnly = true)
    public Optional<Author> findAuthor(Long id) {
        return repository.findById(id);
    }
}

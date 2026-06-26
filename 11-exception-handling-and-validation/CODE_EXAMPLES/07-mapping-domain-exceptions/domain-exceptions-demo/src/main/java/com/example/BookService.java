package com.example;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class BookService {

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    public Book getById(Long id) {
        // Domain language only — no HttpStatus, no ResponseEntity in this layer.
        return repository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    public Book create(Book book) {
        try {
            // saveAndFlush forces the INSERT now, so the unique-constraint failure is thrown
            // INSIDE this try. Plain save() may defer it to commit, escaping the catch.
            return repository.saveAndFlush(book);
        } catch (DataIntegrityViolationException ex) {
            // Translate the framework exception into a domain one the advice knows how to map.
            throw new DuplicateIsbnException(book.getIsbn());
        }
    }
}

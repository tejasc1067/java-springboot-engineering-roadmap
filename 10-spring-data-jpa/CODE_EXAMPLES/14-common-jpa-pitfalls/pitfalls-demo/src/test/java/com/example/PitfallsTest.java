package com.example;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PitfallsTest {

    @Autowired private AuthorRepository authorRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private AuthorService authorService;

    private Long authorId;

    @BeforeEach
    void seed() {
        bookRepository.deleteAll();
        authorRepository.deleteAll();

        Author bloch = new Author("Joshua Bloch");
        bloch.addBook(new Book("Effective Java"));
        bloch.addBook(new Book("Java Puzzlers"));
        authorRepository.save(bloch);
        authorId = bloch.getId();
    }

    @Test
    void lazyCollectionOutsideTransaction_throws() {
        // Service returns the author; the transaction has already ended.
        Author detached = authorService.findAuthor(authorId).orElseThrow();

        // Touching the lazy collection now fails — the session that loaded it is closed.
        assertThatThrownBy(() -> detached.getBooks().size())
                .isInstanceOf(LazyInitializationException.class);
    }

    @Test
    void modifyingDetachedEntity_doesNotPersist() {
        // Outside a transaction: each repository call uses its own short-lived transaction.
        Author author = authorRepository.findById(authorId).orElseThrow();
        // Author is detached after findById returns.
        Book existingBook = bookRepository.findAll().stream()
                .filter(b -> b.getTitle().equals("Effective Java"))
                .findFirst()
                .orElseThrow();

        // Modify the detached entity's field — no effect on the DB.
        existingBook.setTitle("MUTATED");

        // No save() call. The change vanishes.
        Book reloaded = bookRepository.findById(existingBook.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Effective Java");
    }

    @Test
    void modifyingDetached_thenSaveMerges_persistsTheChange() {
        Book existingBook = bookRepository.findAll().stream()
                .filter(b -> b.getTitle().equals("Effective Java"))
                .findFirst()
                .orElseThrow();

        existingBook.setTitle("CORRECTED");
        bookRepository.save(existingBook);   // save() does a merge under the hood

        Book reloaded = bookRepository.findById(existingBook.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("CORRECTED");
    }
}

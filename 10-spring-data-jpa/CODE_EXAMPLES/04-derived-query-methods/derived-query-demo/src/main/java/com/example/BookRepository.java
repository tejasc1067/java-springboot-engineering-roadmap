package com.example;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    // Equality
    List<Book> findByAuthor(String author);

    Optional<Book> findByIsbn(String isbn);

    // Case-insensitive
    List<Book> findByAuthorIgnoreCase(String author);

    // String matching
    List<Book> findByTitleContainingIgnoreCase(String fragment);

    List<Book> findByTitleStartingWith(String prefix);

    // Numeric comparison
    List<Book> findByPublishedYearGreaterThanEqual(int year);

    List<Book> findByPageCountBetween(int low, int high);

    // Multiple criteria
    List<Book> findByAuthorAndPublishedYear(String author, int year);

    List<Book> findByAuthorOrTitle(String author, String title);

    // Collection-shaped input
    List<Book> findByPublishedYearIn(List<Integer> years);

    // Ordering baked into the method name
    List<Book> findByAuthorOrderByPublishedYearDesc(String author);

    // First / Top N
    List<Book> findTop3ByOrderByPublishedYearDesc();

    // Aggregates
    long countByAuthor(String author);

    boolean existsByIsbn(String isbn);
}

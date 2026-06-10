package com.example;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

    // Underscore traversal: book.author.name
    List<Book> findByAuthor_Name(String authorName);

    List<Book> findByAuthor_Country(String country);
}

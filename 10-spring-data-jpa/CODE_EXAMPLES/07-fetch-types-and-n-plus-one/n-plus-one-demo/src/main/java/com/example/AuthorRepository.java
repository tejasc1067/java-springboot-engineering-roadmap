package com.example;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // Naive: triggers N+1 if callers access getBooks() on each result.
    @Query("SELECT a FROM Author a")
    List<Author> findAllNaive();

    // Fix 1: JPQL JOIN FETCH explicitly loads the collection in the same SELECT.
    @Query("SELECT DISTINCT a FROM Author a JOIN FETCH a.books")
    List<Author> findAllWithBooksJoinFetch();

    // Fix 2: @EntityGraph tells Spring Data which associations to eagerly fetch for this call.
    @EntityGraph(attributePaths = "books")
    @Query("SELECT a FROM Author a")
    List<Author> findAllWithBooksEntityGraph();
}

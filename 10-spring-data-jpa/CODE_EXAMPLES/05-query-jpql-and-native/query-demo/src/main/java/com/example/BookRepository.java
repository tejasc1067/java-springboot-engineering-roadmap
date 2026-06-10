package com.example;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

    // JPQL with a positional parameter
    @Query("SELECT b FROM Book b WHERE b.author = ?1")
    List<Book> findByAuthorPositional(String author);

    // JPQL with a named parameter
    @Query("SELECT b FROM Book b WHERE b.author = :author")
    List<Book> findByAuthorNamed(@Param("author") String author);

    // JPQL with multiple conditions, grouped — the OR-with-parens that derived methods can't express
    @Query("""
            SELECT b FROM Book b
            WHERE (b.publishedYear >= :startYear AND b.publishedYear <= :endYear)
              AND (b.author = :author1 OR b.author = :author2)
            """)
    List<Book> findRecentByEitherAuthor(@Param("startYear") int startYear,
                                        @Param("endYear") int endYear,
                                        @Param("author1") String author1,
                                        @Param("author2") String author2);

    // DTO projection via the JPQL "constructor expression"
    @Query("SELECT new com.example.BookSummary(b.title, b.author, b.publishedYear) FROM Book b WHERE b.author = :author")
    List<BookSummary> findSummariesByAuthor(@Param("author") String author);

    // Interface-based projection — Spring builds a proxy that implements TitleAndAuthor
    @Query("SELECT b.title AS title, b.author AS author FROM Book b WHERE b.publishedYear >= :year")
    List<TitleAndAuthor> findTitleAndAuthorByYear(@Param("year") int year);

    // Native SQL — use when JPQL can't express what you need
    @Query(value = "SELECT * FROM books WHERE LENGTH(title) > ?1 ORDER BY LENGTH(title) DESC", nativeQuery = true)
    List<Book> findLongTitlesNative(int minLength);

    // @Modifying — UPDATE / DELETE need this flag, otherwise Spring tries to use them as SELECTs
    @Modifying
    @Query("UPDATE Book b SET b.pageCount = :newCount WHERE b.id = :id")
    int updatePageCount(@Param("id") Long id, @Param("newCount") int newCount);

    @Modifying
    @Query("DELETE FROM Book b WHERE b.publishedYear < :year")
    int deletePublishedBefore(@Param("year") int year);
}

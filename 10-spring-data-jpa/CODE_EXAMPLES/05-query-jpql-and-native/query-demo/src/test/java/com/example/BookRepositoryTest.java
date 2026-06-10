package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BookRepositoryTest {

    @Autowired
    private BookRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
                new Book("Effective Java",             "Joshua Bloch",   2017, 412),
                new Book("Java Concurrency in Practice","Brian Goetz",   2006, 384),
                new Book("Clean Code",                  "Robert Martin", 2008, 464),
                new Book("Clean Architecture",          "Robert Martin", 2017, 432),
                new Book("The Pragmatic Programmer",    "Andy Hunt",     1999, 320)
        ));
    }

    @Test
    void positionalParameter() {
        List<Book> byBloch = repository.findByAuthorPositional("Joshua Bloch");

        assertThat(byBloch).extracting(Book::getTitle).containsExactly("Effective Java");
    }

    @Test
    void namedParameter() {
        List<Book> byBloch = repository.findByAuthorNamed("Joshua Bloch");

        assertThat(byBloch).extracting(Book::getTitle).containsExactly("Effective Java");
    }

    @Test
    void groupedAndOrConditions() {
        List<Book> matches = repository.findRecentByEitherAuthor(2010, 2020, "Joshua Bloch", "Robert Martin");

        assertThat(matches).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Effective Java", "Clean Architecture");
    }

    @Test
    void dtoConstructorProjection() {
        List<BookSummary> summaries = repository.findSummariesByAuthor("Robert Martin");

        assertThat(summaries).hasSize(2);
        assertThat(summaries).extracting(BookSummary::title)
                .containsExactlyInAnyOrder("Clean Code", "Clean Architecture");
        assertThat(summaries.get(0).publishedYear()).isNotNull();
    }

    @Test
    void interfaceProjection() {
        List<TitleAndAuthor> recent = repository.findTitleAndAuthorByYear(2017);

        assertThat(recent).hasSize(2);
        assertThat(recent).allSatisfy(p -> {
            assertThat(p.getTitle()).isNotNull();
            assertThat(p.getAuthor()).isNotNull();
        });
    }

    @Test
    void nativeQuery() {
        List<Book> longTitles = repository.findLongTitlesNative(20);

        assertThat(longTitles).extracting(Book::getTitle)
                .contains("Java Concurrency in Practice", "The Pragmatic Programmer");
    }

    @Test
    void modifyingUpdateReturnsRowsAffected() {
        Book firstBook = repository.findAll().get(0);
        Long id = firstBook.getId();

        int affected = repository.updatePageCount(id, 999);

        assertThat(affected).isEqualTo(1);
    }

    @Test
    void modifyingDeleteReturnsRowsAffected() {
        int affected = repository.deletePublishedBefore(2000);

        assertThat(affected).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(4);
    }
}

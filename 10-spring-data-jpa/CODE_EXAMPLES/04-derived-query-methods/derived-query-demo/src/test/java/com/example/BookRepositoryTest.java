package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
                new Book("Effective Java",            "Joshua Bloch",   2017, "9780134685991", 412),
                new Book("Java Concurrency in Practice","Brian Goetz",  2006, "9780321349606", 384),
                new Book("Clean Code",                 "Robert Martin", 2008, "9780132350884", 464),
                new Book("Clean Architecture",         "Robert Martin", 2017, "9780134494166", 432),
                new Book("The Pragmatic Programmer",   "Andy Hunt",     1999, "9780201616224", 320),
                new Book("Refactoring",                "Martin Fowler", 2018, "9780134757599", 448)
        ));
    }

    @Test
    void equality_findByAuthor() {
        List<Book> byBloch = repository.findByAuthor("Joshua Bloch");

        assertThat(byBloch).extracting(Book::getTitle).containsExactly("Effective Java");
    }

    @Test
    void equality_findByIsbn_returnsOptional() {
        Optional<Book> found = repository.findByIsbn("9780134685991");
        Optional<Book> missing = repository.findByIsbn("0000000000000");

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Effective Java");
        assertThat(missing).isEmpty();
    }

    @Test
    void caseInsensitive_findByAuthorIgnoreCase() {
        List<Book> sameCase = repository.findByAuthor("joshua bloch");
        List<Book> ignoreCase = repository.findByAuthorIgnoreCase("joshua bloch");

        assertThat(sameCase).isEmpty();
        assertThat(ignoreCase).extracting(Book::getTitle).containsExactly("Effective Java");
    }

    @Test
    void stringMatch_findByTitleContainingIgnoreCase() {
        List<Book> clean = repository.findByTitleContainingIgnoreCase("clean");

        assertThat(clean).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Clean Code", "Clean Architecture");
    }

    @Test
    void stringMatch_findByTitleStartingWith() {
        List<Book> starts = repository.findByTitleStartingWith("Clean");

        assertThat(starts).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Clean Code", "Clean Architecture");
    }

    @Test
    void numeric_findByPublishedYearGreaterThanEqual() {
        List<Book> recent = repository.findByPublishedYearGreaterThanEqual(2017);

        assertThat(recent).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Effective Java", "Clean Architecture", "Refactoring");
    }

    @Test
    void numeric_findByPageCountBetween() {
        List<Book> midSized = repository.findByPageCountBetween(400, 450);

        assertThat(midSized).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Effective Java", "Clean Architecture", "Refactoring");
    }

    @Test
    void multiCriteria_findByAuthorAndPublishedYear() {
        List<Book> hit = repository.findByAuthorAndPublishedYear("Robert Martin", 2008);
        List<Book> miss = repository.findByAuthorAndPublishedYear("Robert Martin", 1990);

        assertThat(hit).extracting(Book::getTitle).containsExactly("Clean Code");
        assertThat(miss).isEmpty();
    }

    @Test
    void multiCriteria_findByAuthorOrTitle() {
        List<Book> either = repository.findByAuthorOrTitle("Joshua Bloch", "Refactoring");

        assertThat(either).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Effective Java", "Refactoring");
    }

    @Test
    void collection_findByPublishedYearIn() {
        List<Book> picked = repository.findByPublishedYearIn(List.of(1999, 2018));

        assertThat(picked).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("The Pragmatic Programmer", "Refactoring");
    }

    @Test
    void ordering_findByAuthorOrderByPublishedYearDesc() {
        List<Book> ordered = repository.findByAuthorOrderByPublishedYearDesc("Robert Martin");

        assertThat(ordered).extracting(Book::getTitle).containsExactly("Clean Architecture", "Clean Code");
    }

    @Test
    void topN_findTop3ByOrderByPublishedYearDesc() {
        List<Book> top3 = repository.findTop3ByOrderByPublishedYearDesc();

        assertThat(top3).hasSize(3);
        assertThat(top3).extracting(Book::getPublishedYear).containsExactly(2018, 2017, 2017);
    }

    @Test
    void aggregates_countByAuthor() {
        long martin = repository.countByAuthor("Robert Martin");
        long missing = repository.countByAuthor("Nobody");

        assertThat(martin).isEqualTo(2);
        assertThat(missing).isZero();
    }

    @Test
    void aggregates_existsByIsbn() {
        assertThat(repository.existsByIsbn("9780134685991")).isTrue();
        assertThat(repository.existsByIsbn("0000000000000")).isFalse();
    }
}

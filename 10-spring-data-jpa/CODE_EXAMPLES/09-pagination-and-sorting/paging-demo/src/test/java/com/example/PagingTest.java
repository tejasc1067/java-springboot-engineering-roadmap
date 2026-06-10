package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PagingTest {

    @Autowired
    private BookRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
                new Book("Effective Java",                "Joshua Bloch",   2017),
                new Book("Java Puzzlers",                 "Joshua Bloch",   2005),
                new Book("Java Concurrency in Practice", "Joshua Bloch",   2006),
                new Book("Clean Code",                    "Robert Martin", 2008),
                new Book("Clean Architecture",            "Robert Martin", 2017),
                new Book("Clean Agile",                   "Robert Martin", 2019),
                new Book("The Pragmatic Programmer",      "Andy Hunt",     1999),
                new Book("Refactoring",                   "Martin Fowler", 2018)
        ));
    }

    @Test
    void findAllWithSort() {
        List<Book> byYearAsc = repository.findAll(Sort.by("publishedYear").ascending());

        assertThat(byYearAsc).extracting(Book::getPublishedYear)
                .containsExactly(1999, 2005, 2006, 2008, 2017, 2017, 2018, 2019);
    }

    @Test
    void findAllWithMultipleSortFields() {
        Sort sort = Sort.by(Sort.Order.asc("publishedYear"), Sort.Order.asc("title"));
        List<Book> ordered = repository.findAll(sort);

        // 2017 has two books — Bloch's Effective Java and Martin's Clean Architecture.
        // After publishedYear asc, ties break on title asc -> Clean Architecture before Effective Java.
        List<String> titles = ordered.stream().map(Book::getTitle).toList();
        int architectureIdx = titles.indexOf("Clean Architecture");
        int effectiveIdx = titles.indexOf("Effective Java");
        assertThat(architectureIdx).isLessThan(effectiveIdx);
    }

    @Test
    void pageReturnsContentAndMetadata() {
        Page<Book> page = repository.findByAuthor("Robert Martin", PageRequest.of(0, 2, Sort.by("publishedYear").ascending()));

        assertThat(page.getContent()).extracting(Book::getTitle)
                .containsExactly("Clean Code", "Clean Architecture");
        assertThat(page.getTotalElements()).isEqualTo(3);   // 3 books by Martin total
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getNumber()).isZero();              // zero-indexed
        assertThat(page.isFirst()).isTrue();
        assertThat(page.isLast()).isFalse();
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void pageSecondPage() {
        Page<Book> page = repository.findByAuthor("Robert Martin", PageRequest.of(1, 2, Sort.by("publishedYear").ascending()));

        assertThat(page.getContent()).extracting(Book::getTitle)
                .containsExactly("Clean Agile");
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.isLast()).isTrue();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void emptyPageWhenAuthorHasNothing() {
        Page<Book> page = repository.findByAuthor("Nobody", PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
    }

    @Test
    void sliceKnowsAboutNextWithoutCountingTotal() {
        Slice<Book> slice = repository.findSliceByAuthor("Robert Martin", PageRequest.of(0, 2, Sort.by("publishedYear").ascending()));

        assertThat(slice.getContent()).hasSize(2);
        assertThat(slice.hasNext()).isTrue();
        // Slice does NOT expose totalElements / totalPages — only the current page contents and hasNext.
    }

    @Test
    void pageMapTransformsContents() {
        Page<Book> page = repository.findByAuthor("Joshua Bloch", PageRequest.of(0, 10));

        Page<String> titlesPage = page.map(Book::getTitle);

        assertThat(titlesPage.getContent()).contains("Effective Java", "Java Puzzlers", "Java Concurrency in Practice");
        assertThat(titlesPage.getTotalElements()).isEqualTo(3);  // metadata preserved
    }
}

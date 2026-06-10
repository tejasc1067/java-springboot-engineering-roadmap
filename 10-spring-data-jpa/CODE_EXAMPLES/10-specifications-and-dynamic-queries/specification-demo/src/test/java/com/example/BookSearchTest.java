package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookSearchTest {

    @Autowired
    private BookSearchService service;

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
                new Book("The Pragmatic Programmer",      "Andy Hunt",     1999),
                new Book("Refactoring",                   "Martin Fowler", 2018)
        ));
    }

    @Test
    void emptyCriteriaReturnsEverything() {
        List<Book> all = service.search(new BookSearchCriteria(null, null, null, null));

        assertThat(all).hasSize(7);
    }

    @Test
    void singleFilter_author() {
        List<Book> result = service.search(new BookSearchCriteria("Joshua Bloch", null, null, null));

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(b -> assertThat(b.getAuthor()).isEqualTo("Joshua Bloch"));
    }

    @Test
    void singleFilter_titleFragment_caseInsensitive() {
        List<Book> result = service.search(new BookSearchCriteria(null, "CLEAN", null, null));

        assertThat(result).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Clean Code", "Clean Architecture");
    }

    @Test
    void combinedFilters_authorAndYearRange() {
        List<Book> result = service.search(new BookSearchCriteria("Joshua Bloch", null, 2006, 2017));

        assertThat(result).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Effective Java", "Java Concurrency in Practice");
    }

    @Test
    void combinedFilters_titleAndYear() {
        List<Book> result = service.search(new BookSearchCriteria(null, "java", 2010, null));

        assertThat(result).extracting(Book::getTitle).containsExactly("Effective Java");
    }

    @Test
    void noMatches() {
        List<Book> result = service.search(new BookSearchCriteria("Nobody", null, null, null));

        assertThat(result).isEmpty();
    }

    @Test
    void withPagination() {
        Page<Book> page = service.search(
                new BookSearchCriteria(null, null, 2000, null),
                PageRequest.of(0, 3, Sort.by("publishedYear").ascending()));

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(6);  // 7 books minus the 1999 one
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent().get(0).getPublishedYear()).isEqualTo(2005);
    }

    @Test
    void rawSpecificationAlsoWorksOnRepository() {
        // Specifications can be composed inline at the repository, not only via a service.
        List<Book> result = repository.findAll(
                BookSpecs.hasAuthor("Robert Martin")
                         .and(BookSpecs.publishedYearAtLeast(2010)));

        assertThat(result).extracting(Book::getTitle).containsExactly("Clean Architecture");
    }
}

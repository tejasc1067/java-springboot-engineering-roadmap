package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// @DataJpaTest:
//   - boots only the JPA slice (no web, no service-layer beans)
//   - wraps every test in a transaction that rolls back at the end (clean state per test)
//   - swaps in an embedded H2 by default (here we already use H2, so no swap happens)
@DataJpaTest
class BookRepositorySliceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private BookRepository repository;

    @Test
    void persistAndFlush_returnsTheSavedEntity() {
        Book saved = em.persistAndFlush(new Book("Effective Java", "Joshua Bloch", 2017));

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void findByAuthor() {
        em.persist(new Book("Effective Java", "Joshua Bloch", 2017));
        em.persist(new Book("Java Puzzlers", "Joshua Bloch", 2005));
        em.persist(new Book("Clean Code", "Robert Martin", 2008));
        em.flush();

        List<Book> byBloch = repository.findByAuthor("Joshua Bloch");

        assertThat(byBloch).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Effective Java", "Java Puzzlers");
    }

    @Test
    void countByYear() {
        em.persist(new Book("Effective Java", "Joshua Bloch", 2017));
        em.persist(new Book("Clean Architecture", "Robert Martin", 2017));
        em.persist(new Book("The Pragmatic Programmer", "Andy Hunt", 1999));
        em.flush();

        long recent = repository.countByPublishedYearGreaterThanEqual(2010);

        assertThat(recent).isEqualTo(2);
    }

    @Test
    void rolledBackBetweenTests_previousDataIsGone() {
        // Each test starts with an empty database — the previous test's inserts were rolled back.
        assertThat(repository.count()).isZero();
    }
}

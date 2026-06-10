package com.example;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BookEntityTest {

    @Autowired
    private EntityManager em;

    @Test
    void persistAssignsAnId() {
        Book book = new Book("Effective Java", "Joshua Bloch", 2017);
        assertThat(book.getId()).isNull();

        em.persist(book);
        em.flush();

        assertThat(book.getId()).isNotNull();
        assertThat(book.getId()).isPositive();
    }

    @Test
    void roundTripThroughTheDatabase() {
        Book saved = new Book("Java Concurrency in Practice", "Brian Goetz", 2006);
        em.persist(saved);
        em.flush();
        em.clear();

        Book loaded = em.find(Book.class, saved.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded).isNotSameAs(saved);
        assertThat(loaded.getTitle()).isEqualTo("Java Concurrency in Practice");
        assertThat(loaded.getAuthor()).isEqualTo("Brian Goetz");
        assertThat(loaded.getPublishedYear()).isEqualTo(2006);
    }

    @Test
    void twoInsertsGetIncrementingIds() {
        Book first = new Book("Clean Code", "Robert Martin", 2008);
        Book second = new Book("The Pragmatic Programmer", "Andy Hunt", 1999);

        em.persist(first);
        em.persist(second);
        em.flush();

        assertThat(second.getId()).isGreaterThan(first.getId());
    }
}

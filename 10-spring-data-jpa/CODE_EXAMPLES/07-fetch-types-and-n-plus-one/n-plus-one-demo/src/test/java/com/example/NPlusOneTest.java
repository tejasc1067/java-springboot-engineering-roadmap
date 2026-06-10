package com.example;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NPlusOneTest {

    @Autowired
    private AuthorRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics stats;

    @BeforeEach
    void seedAndEnableStats() {
        repository.deleteAll();

        Author bloch = new Author("Joshua Bloch");
        bloch.addBook(new Book("Effective Java"));
        bloch.addBook(new Book("Java Puzzlers"));

        Author goetz = new Author("Brian Goetz");
        goetz.addBook(new Book("Java Concurrency in Practice"));

        Author martin = new Author("Robert Martin");
        martin.addBook(new Book("Clean Code"));
        martin.addBook(new Book("Clean Architecture"));

        repository.saveAll(List.of(bloch, goetz, martin));
        repository.flush();
        // Evict everything from the persistence context so the test queries hit the database.
        entityManager.clear();

        SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
        stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
    }

    @Test
    void naiveQueryTriggersNPlusOne() {
        List<Author> authors = repository.findAllNaive();
        long queriesAfterListLoad = stats.getPrepareStatementCount();

        // Touching each author's books triggers a lazy SELECT per author.
        authors.forEach(a -> a.getBooks().size());

        long totalQueries = stats.getPrepareStatementCount();
        long lazyLoads = totalQueries - queriesAfterListLoad;

        assertThat(authors).hasSize(3);
        assertThat(queriesAfterListLoad).isEqualTo(1);     // 1 SELECT for the authors
        assertThat(lazyLoads).isEqualTo(3);                // N more SELECTs, one per author
    }

    @Test
    void joinFetchLoadsBooksInOneQuery() {
        List<Author> authors = repository.findAllWithBooksJoinFetch();
        long queriesAfterListLoad = stats.getPrepareStatementCount();

        // Books are already loaded — no extra SELECTs on access.
        authors.forEach(a -> a.getBooks().size());

        long totalQueries = stats.getPrepareStatementCount();

        assertThat(authors).hasSize(3);
        assertThat(authors).allSatisfy(a -> assertThat(a.getBooks()).isNotEmpty());
        assertThat(totalQueries).isEqualTo(1);
        assertThat(queriesAfterListLoad).isEqualTo(1);
    }

    @Test
    void entityGraphLoadsBooksInOneQuery() {
        List<Author> authors = repository.findAllWithBooksEntityGraph();
        long queriesAfterListLoad = stats.getPrepareStatementCount();

        authors.forEach(a -> a.getBooks().size());

        long totalQueries = stats.getPrepareStatementCount();

        assertThat(authors).hasSize(3);
        assertThat(totalQueries).isEqualTo(1);
        assertThat(queriesAfterListLoad).isEqualTo(1);
    }
}

package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
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
    void clear() {
        repository.deleteAll();
    }

    @Test
    void saveAssignsAnId() {
        Book saved = repository.save(new Book("Effective Java", "Joshua Bloch", 2017));

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void findByIdReturnsAnOptional() {
        Book saved = repository.save(new Book("Clean Code", "Robert Martin", 2008));

        Optional<Book> found = repository.findById(saved.getId());
        Optional<Book> missing = repository.findById(99_999L);

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Clean Code");
        assertThat(missing).isEmpty();
    }

    @Test
    void findAllReturnsEverything() {
        repository.save(new Book("Effective Java", "Joshua Bloch", 2017));
        repository.save(new Book("Clean Code", "Robert Martin", 2008));
        repository.save(new Book("The Pragmatic Programmer", "Andy Hunt", 1999));

        List<Book> all = repository.findAll();

        assertThat(all).hasSize(3);
    }

    @Test
    void findAllWithSortOrdersResults() {
        repository.save(new Book("Effective Java", "Joshua Bloch", 2017));
        repository.save(new Book("Clean Code", "Robert Martin", 2008));
        repository.save(new Book("The Pragmatic Programmer", "Andy Hunt", 1999));

        List<Book> byYear = repository.findAll(Sort.by("publishedYear").ascending());

        assertThat(byYear).extracting(Book::getPublishedYear).containsExactly(1999, 2008, 2017);
    }

    @Test
    void saveAllReturnsAList() {
        List<Book> saved = repository.saveAll(List.of(
                new Book("Effective Java", "Joshua Bloch", 2017),
                new Book("Clean Code", "Robert Martin", 2008)
        ));

        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(b -> assertThat(b.getId()).isNotNull());
    }

    @Test
    void existsByIdAndCount() {
        Book saved = repository.save(new Book("Effective Java", "Joshua Bloch", 2017));

        assertThat(repository.existsById(saved.getId())).isTrue();
        assertThat(repository.existsById(99_999L)).isFalse();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void deleteById() {
        Book saved = repository.save(new Book("Effective Java", "Joshua Bloch", 2017));

        repository.deleteById(saved.getId());

        assertThat(repository.findById(saved.getId())).isEmpty();
        assertThat(repository.count()).isZero();
    }

    @Test
    void updateThroughDirtyChecking() {
        Book saved = repository.save(new Book("Effective Java", "Joshua Bloch", 2017));

        Book managed = repository.findById(saved.getId()).orElseThrow();
        managed.setTitle("Effective Java, 3rd Edition");

        Book reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Effective Java, 3rd Edition");
    }
}

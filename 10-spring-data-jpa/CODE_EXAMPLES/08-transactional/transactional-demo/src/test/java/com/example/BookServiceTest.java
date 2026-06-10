package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BookServiceTest {

    @Autowired
    private BookService service;

    @Autowired
    private BookRepository repository;

    private Long bookId;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        Book saved = repository.save(new Book("Original Title", 300));
        bookId = saved.getId();
    }

    @Test
    void dirtyChecking_persistsChangeWithoutExplicitSave() {
        service.renameBook(bookId, "Renamed");

        Book reloaded = repository.findById(bookId).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Renamed");
    }

    @Test
    void runtimeException_rollsBack() {
        assertThatThrownBy(() -> service.renameAndThrowRuntime(bookId, "Renamed"))
                .isInstanceOf(RuntimeException.class);

        Book reloaded = repository.findById(bookId).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Original Title");  // rolled back
    }

    @Test
    void checkedException_doesNotRollBack_byDefault() {
        assertThatThrownBy(() -> service.renameAndThrowChecked(bookId, "Renamed"))
                .isInstanceOf(OutOfPagesException.class);

        Book reloaded = repository.findById(bookId).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Renamed");  // committed despite the exception
    }

    @Test
    void checkedException_withRollbackFor_doesRollBack() {
        assertThatThrownBy(() -> service.renameAndThrowChecked_withRollbackFor(bookId, "Renamed"))
                .isInstanceOf(OutOfPagesException.class);

        Book reloaded = repository.findById(bookId).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Original Title");  // rolled back
    }

    @Test
    void readOnly_doesNotFlushDirtyChange() {
        service.renameInReadOnly(bookId, "Renamed");

        Book reloaded = repository.findById(bookId).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("Original Title");  // not persisted
    }

    @Test
    void selfInvocation_bypassesTransactional_soChangeIsNotPersisted() {
        service.renameViaSelfInvocation(bookId, "Renamed");

        Book reloaded = repository.findById(bookId).orElseThrow();
        // Without the @Transactional proxy, findById's short-lived transaction returns the Book
        // detached, so setTitle has no effect on the database.
        assertThat(reloaded.getTitle()).isEqualTo("Original Title");
    }
}

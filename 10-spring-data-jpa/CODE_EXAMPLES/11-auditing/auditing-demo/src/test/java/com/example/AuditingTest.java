package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AuditingTest {

    @Autowired
    private BookRepository repository;

    @Test
    void createdFieldsAreSetOnInsert() {
        Instant before = Instant.now();
        Book saved = repository.saveAndFlush(new Book("Effective Java"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(saved.getCreatedBy()).isEqualTo("test-user");
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(saved.getUpdatedBy()).isEqualTo("test-user");
    }

    @Test
    void updatedFieldsChangeOnSave_createdFieldsDoNot() throws InterruptedException {
        Book saved = repository.saveAndFlush(new Book("Original"));
        Instant originalCreated = saved.getCreatedAt();
        Instant originalUpdated = saved.getUpdatedAt();

        // Force a measurable time gap so updatedAt advances visibly.
        Thread.sleep(10);

        saved.setTitle("Updated");
        repository.saveAndFlush(saved);

        Book reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isEqualTo(originalCreated);   // unchanged
        assertThat(reloaded.getUpdatedAt()).isAfter(originalUpdated);     // advanced
    }
}

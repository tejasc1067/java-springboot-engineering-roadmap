package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class FlywayTest {

    @Autowired
    private BookRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsRanAndCreatedTheBooksTable() {
        // V3 seeded two rows.
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void seedDataLooksRight() {
        List<Book> all = repository.findAll();
        assertThat(all).extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Effective Java", "Clean Code");
        assertThat(all).allSatisfy(b -> assertThat(b.getIsbn()).isNotNull());
    }

    @Test
    void flywayRecordedItsHistory() {
        // Every Flyway-managed DB has this table — it tracks which migrations ran.
        // H2 keeps Flyway's table and column names lowercase, so quote them.
        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE",
                Integer.class);

        // V1, V2, V3 — and Flyway may also record a baseline row, so be tolerant.
        assertThat(rowCount).isGreaterThanOrEqualTo(3);
    }
}

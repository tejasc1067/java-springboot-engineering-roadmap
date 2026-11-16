package com.example.idempotent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {"payments-naive", "payments-idempotent"})
class NaturalIdempotencyTest {

    @Autowired
    AccountService accounts;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void setToAValue_appliedTwice_isTheSameAsOnce() {
        accounts.setEmail("ann", "ann@example.com");
        accounts.setEmail("ann", "ann@example.com");   // the redelivery

        assertThat(jdbc.queryForList("SELECT email FROM customer_email WHERE customer = 'ann'", String.class))
                .containsExactly("ann@example.com");
    }
}

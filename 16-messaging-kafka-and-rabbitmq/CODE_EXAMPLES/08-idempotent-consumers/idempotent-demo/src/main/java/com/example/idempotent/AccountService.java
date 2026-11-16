package com.example.idempotent;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final JdbcTemplate jdbc;

    public AccountService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Broken: every delivery adds the amount again. */
    @Transactional
    public void creditNaive(Payment payment) {
        jdbc.update("UPDATE account SET balance = balance + ? WHERE id = ?", payment.amount(), payment.account());
    }

    /**
     * Fixed: record the event id and change the balance in ONE transaction.
     * A duplicate fails on the primary key, so the balance update never runs for it.
     * If the transaction rolls back, neither the id nor the credit is saved, so a retry can still apply it.
     */
    @Transactional
    public boolean creditOnce(Payment payment) {
        try {
            jdbc.update("INSERT INTO processed_event (event_id) VALUES (?)", payment.eventId());
        } catch (DuplicateKeyException alreadyApplied) {
            return false;
        }
        jdbc.update("UPDATE account SET balance = balance + ? WHERE id = ?", payment.amount(), payment.account());
        return true;
    }

    /** Broken under concurrency: "have I seen it?" and "remember it" are two steps with nothing stopping a second caller in between. */
    @Transactional
    public boolean creditCheckThenInsert(Payment payment, Runnable afterCheck) {
        Integer seen = jdbc.queryForObject("SELECT COUNT(*) FROM processed_event_no_key WHERE event_id = ?",
                Integer.class, payment.eventId());
        if (seen > 0) {
            return false;
        }
        afterCheck.run();   // the test holds both callers here
        jdbc.update("INSERT INTO processed_event_no_key (event_id) VALUES (?)", payment.eventId());
        jdbc.update("UPDATE account SET balance = balance + ? WHERE id = ?", payment.amount(), payment.account());
        return true;
    }

    /** Naturally idempotent: "the email is X", not "add X". Applying it twice leaves the same row. */
    @Transactional
    public void setEmail(String customer, String email) {
        jdbc.update("MERGE INTO customer_email (customer, email) KEY (customer) VALUES (?, ?)", customer, email);
    }

    public long balanceOf(String account) {
        return jdbc.queryForObject("SELECT balance FROM account WHERE id = ?", Long.class, account);
    }

    public void openAccount(String account) {
        jdbc.update("MERGE INTO account (id, balance) KEY (id) VALUES (?, 0)", account);
    }
}

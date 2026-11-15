package com.example;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaAndTransactionsTest {

    @Test
    void documentStore_acceptsAMisspelledField() {
        DocumentStore users = new DocumentStore();
        users.save("u1", DocumentStore.newDocument().put("_id", "u1").put("emial", "bob@example.com"));

        // Saved without complaint. The first code that reads "email" finds nothing.
        assertThat(users.findById("u1").get("email")).isNull();
    }

    @Test
    void relationalSchema_rejectsTheSameMistake() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:h2:mem:schema;DB_CLOSE_DELAY=-1");
             Statement sql = db.createStatement()) {
            sql.execute("create table users (id varchar primary key, email varchar not null)");

            assertThatThrownBy(() -> sql.execute("insert into users (id, emial) values ('u1', 'bob@example.com')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("EMIAL");
        }
    }

    @Test
    void twoDocumentUpdate_withoutATransaction_canStopHalfway() {
        DocumentStore accounts = new DocumentStore();
        accounts.save("a", account("a", 100));
        accounts.save("b", account("b", 0));

        try {
            accounts.save("a", account("a", 70));            // debit 30
            throw new IllegalStateException("crash before the credit");
            // accounts.save("b", account("b", 30));         // never runs
        } catch (IllegalStateException expected) {
        }

        int total = accounts.findById("a").get("balance").asInt() + accounts.findById("b").get("balance").asInt();
        assertThat(total).isEqualTo(70); // 30 has vanished
    }

    @Test
    void relationalTransaction_rollsBackBothRows() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:h2:mem:tx;DB_CLOSE_DELAY=-1");
             Statement sql = db.createStatement()) {
            sql.execute("create table account (id varchar primary key, balance int)");
            sql.execute("insert into account values ('a', 100), ('b', 0)");

            db.setAutoCommit(false);
            try {
                sql.executeUpdate("update account set balance = balance - 30 where id = 'a'");
                throw new IllegalStateException("crash before the credit");
            } catch (IllegalStateException expected) {
                db.rollback();
            }
            db.setAutoCommit(true);

            try (ResultSet rs = sql.executeQuery("select sum(balance) from account")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(100);
            }
        }
    }

    private static ObjectNode account(String id, int balance) {
        return DocumentStore.newDocument().put("_id", id).put("balance", balance);
    }
}

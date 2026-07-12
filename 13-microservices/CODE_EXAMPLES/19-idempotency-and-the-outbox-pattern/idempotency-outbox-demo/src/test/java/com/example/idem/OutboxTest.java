package com.example.idem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transactional outbox, on a real H2 transaction so the atomicity is genuine.
 *
 * The dual-write problem (topic 07): a service commits its business change to its DB and
 * then publishes an event to the broker — two separate writes to two systems. A crash
 * between them loses the event (or publishing first and then failing the DB write emits a
 * phantom event). You cannot make two systems commit atomically without a distributed
 * transaction (topic 18 says: don't).
 *
 * The fix: write the event into an "outbox" table in the SAME local transaction as the
 * business row. Now they commit together or not at all. A separate relay reads unpublished
 * outbox rows and publishes them (at-least-once), so the event is never lost — and
 * consumers dedupe with idempotency keys because delivery is at-least-once.
 */
class OutboxTest {

    private static final String URL = "jdbc:h2:mem:outboxdemo;DB_CLOSE_DELAY=-1";

    @BeforeEach
    void resetSchema() throws SQLException {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS orders");
            s.execute("DROP TABLE IF EXISTS outbox");
            s.execute("CREATE TABLE orders (id INT PRIMARY KEY, sku VARCHAR, status VARCHAR)");
            s.execute("CREATE TABLE outbox (id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "event_type VARCHAR, payload VARCHAR, published BOOLEAN DEFAULT FALSE)");
        }
    }

    @Test
    void dualWriteLosesTheEventWhenTheProcessCrashesBeforePublishing() throws SQLException {
        List<String> broker = new ArrayList<>();

        // Commit the order... then "crash" before the separate publish call runs.
        try (Connection c = open()) {
            insertOrder(c, 1, "SKU-BOOK");
        }
        // (process dies here — broker.add(...) never happened)

        assertThat(orderCount()).isEqualTo(1); // business change is committed...
        assertThat(broker).isEmpty();          // ...but the event was never published: LOST.
    }

    @Test
    void outboxWritesTheOrderAndEventAtomicallyThenTheRelayPublishes() throws SQLException {
        // One local transaction: order row + outbox row commit together.
        placeOrderWithOutbox(1, "SKU-BOOK");

        assertThat(orderCount()).isEqualTo(1);
        assertThat(unpublishedOutboxCount()).isEqualTo(1); // event is safely recorded, awaiting relay

        List<String> broker = new ArrayList<>();
        relayPublishOnce(broker);

        assertThat(broker).containsExactly("OrderPlaced:1"); // relay published it — not lost
        assertThat(unpublishedOutboxCount()).isZero();       // marked published
    }

    @Test
    void aFailureBeforeCommitLeavesNeitherAnOrderNorAPhantomEvent() throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            insertOrderTx(c, 1, "SKU-BOOK");
            insertOutboxTx(c, "OrderPlaced", "OrderPlaced:1");
            // something fails before commit -> roll the WHOLE local transaction back
            c.rollback();
        }

        assertThat(orderCount()).isZero();             // no half-written business change
        assertThat(unpublishedOutboxCount()).isZero(); // and no phantom event to publish
    }

    @Test
    void theRelayIsIdempotentAcrossReruns() throws SQLException {
        placeOrderWithOutbox(1, "SKU-BOOK");
        List<String> broker = new ArrayList<>();

        relayPublishOnce(broker);
        relayPublishOnce(broker); // runs again (e.g. after a relay restart)

        assertThat(broker).containsExactly("OrderPlaced:1"); // published once, not twice
    }

    // --- the outbox write: business row + event row in ONE transaction ---
    private void placeOrderWithOutbox(int orderId, String sku) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            insertOrderTx(c, orderId, sku);
            insertOutboxTx(c, "OrderPlaced", "OrderPlaced:" + orderId);
            c.commit(); // both or neither
        }
    }

    // --- the relay: publish unpublished outbox rows, then mark them published ---
    private void relayPublishOnce(List<String> broker) throws SQLException {
        try (Connection c = open()) {
            List<int[]> toMark = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, payload FROM outbox WHERE published = FALSE ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    broker.add(rs.getString("payload")); // "publish" to the broker
                    toMark.add(new int[]{rs.getInt("id")});
                }
            }
            for (int[] row : toMark) {
                try (PreparedStatement upd = c.prepareStatement(
                        "UPDATE outbox SET published = TRUE WHERE id = ?")) {
                    upd.setInt(1, row[0]);
                    upd.executeUpdate();
                }
            }
        }
    }

    // --- small JDBC helpers ---
    private Connection open() throws SQLException {
        return DriverManager.getConnection(URL, "sa", "");
    }

    private void insertOrder(Connection c, int id, String sku) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO orders(id, sku, status) VALUES (?, ?, 'CONFIRMED')")) {
            ps.setInt(1, id);
            ps.setString(2, sku);
            ps.executeUpdate();
        }
    }

    private void insertOrderTx(Connection c, int id, String sku) throws SQLException {
        insertOrder(c, id, sku);
    }

    private void insertOutboxTx(Connection c, String type, String payload) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO outbox(event_type, payload) VALUES (?, ?)")) {
            ps.setString(1, type);
            ps.setString(2, payload);
            ps.executeUpdate();
        }
    }

    private int orderCount() throws SQLException {
        return count("SELECT COUNT(*) FROM orders");
    }

    private int unpublishedOutboxCount() throws SQLException {
        return count("SELECT COUNT(*) FROM outbox WHERE published = FALSE");
    }

    private int count(String sql) throws SQLException {
        try (Connection c = open(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}

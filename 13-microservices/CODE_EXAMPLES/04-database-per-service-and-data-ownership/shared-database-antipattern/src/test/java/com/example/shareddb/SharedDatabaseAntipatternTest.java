package com.example.shareddb;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shared-database anti-pattern, made observable.
 *
 * Two services — "inventory" and "orders" — share ONE database and ONE `product`
 * table. The orders side reads inventory's table directly with SQL. That looks
 * convenient and fast. Then the inventory team does a completely normal thing:
 * they rename a column. Because the orders side reached into inventory's schema,
 * the orders side breaks — at runtime, in a service the inventory team may not
 * even know exists.
 *
 * The lesson: sharing a database means neither service can change its schema
 * without risking the other. They are not independently deployable, which means
 * they are not really separate services (topic 03). The fix is one database per
 * service, reached only through an API — exactly what the order-inventory-system
 * scaffold (elsewhere in this module's CODE_EXAMPLES) demonstrates.
 */
class SharedDatabaseAntipatternTest {

    // One shared, in-memory database that both "services" connect to.
    private static final String SHARED_DB = "jdbc:h2:mem:shared;DB_CLOSE_DELAY=-1";

    @Test
    void aSchemaChangeInOneServiceBreaksTheOther() throws SQLException {
        try (Connection conn = DriverManager.getConnection(SHARED_DB, "sa", "")) {

            // --- inventory-service owns and creates the product table ---
            exec(conn, "CREATE TABLE product (sku VARCHAR PRIMARY KEY, stock INT)");
            exec(conn, "INSERT INTO product VALUES ('SKU-BOOK', 5)");

            // --- orders-service reaches directly into inventory's table (the anti-pattern) ---
            int stockBefore = readStockLikeTheOrdersService(conn, "SKU-BOOK");
            assertThat(stockBefore).isEqualTo(5); // works today

            // --- inventory-service evolves ITS OWN schema: rename stock -> available_qty.
            //     A change the inventory team is fully entitled to make to their own table. ---
            exec(conn, "ALTER TABLE product ALTER COLUMN stock RENAME TO available_qty");

            // --- the same orders-service query now blows up. Nothing in orders-service
            //     changed; it broke because it depended on inventory's private schema. ---
            assertThatThrownBy(() -> readStockLikeTheOrdersService(conn, "SKU-BOOK"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("STOCK"); // column no longer exists

            System.out.println("orders-service broke on 'SELECT stock ...' after inventory "
                    + "renamed its own column. Sharing a database couples their schemas: "
                    + "neither can be deployed independently. One DB per service is the fix.");
        }
    }

    /** How the orders-service reads stock in the anti-pattern: direct SQL on inventory's table. */
    private int readStockLikeTheOrdersService(Connection conn, String sku) throws SQLException {
        try (var ps = conn.prepareStatement("SELECT stock FROM product WHERE sku = ?")) {
            ps.setString(1, sku);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("stock");
            }
        }
    }

    private void exec(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }
}

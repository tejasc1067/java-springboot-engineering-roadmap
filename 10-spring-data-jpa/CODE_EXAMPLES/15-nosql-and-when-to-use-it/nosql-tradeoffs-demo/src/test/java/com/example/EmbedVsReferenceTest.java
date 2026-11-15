package com.example;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbedVsReferenceTest {

    private static ObjectNode order(String id, String customerId, String customerName) {
        ObjectNode order = DocumentStore.newDocument();
        order.put("_id", id);
        order.put("customerId", customerId);
        order.put("customerName", customerName);       // embedded copy of the customer's name
        order.putArray("items").addObject().put("sku", "BOOK-1").put("qty", 2);
        return order;
    }

    @Test
    void embeddedOrder_isReadInOneLookup() {
        DocumentStore orders = new DocumentStore();
        orders.save("o1", order("o1", "c1", "Ann Lee"));

        ObjectNode order = orders.findById("o1");

        // Customer name and items arrive together: the document is shaped like the screen that shows it.
        assertThat(order.get("customerName").asText()).isEqualTo("Ann Lee");
        assertThat(order.get("items")).hasSize(1);
        assertThat(orders.lookups()).isEqualTo(1);
    }

    @Test
    void embeddedCopy_goesStaleWhenTheOriginalChanges() {
        DocumentStore customers = new DocumentStore();
        DocumentStore orders = new DocumentStore();
        ObjectNode ann = DocumentStore.newDocument().put("_id", "c1").put("name", "Ann Lee");
        customers.save("c1", ann);
        orders.save("o1", order("o1", "c1", "Ann Lee"));
        orders.save("o2", order("o2", "c1", "Ann Lee"));

        // Ann changes her name. The customer document is updated...
        customers.save("c1", ann.put("name", "Ann Park"));

        // ...but every order still holds the copy made when it was written.
        assertThat(customers.findById("c1").get("name").asText()).isEqualTo("Ann Park");
        assertThat(orders.findById("o1").get("customerName").asText()).isEqualTo("Ann Lee");
        assertThat(orders.findById("o2").get("customerName").asText()).isEqualTo("Ann Lee");
    }

    @Test
    void relationalRename_isOneUpdateAndVisibleEverywhere() throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:h2:mem:embed;DB_CLOSE_DELAY=-1");
             Statement sql = db.createStatement()) {
            sql.execute("create table customer (id varchar primary key, name varchar not null)");
            sql.execute("create table orders (id varchar primary key, customer_id varchar references customer(id))");
            sql.execute("insert into customer values ('c1', 'Ann Lee')");
            sql.execute("insert into orders values ('o1', 'c1'), ('o2', 'c1')");

            // The name is stored once; orders point at it.
            sql.executeUpdate("update customer set name = 'Ann Park' where id = 'c1'");

            List<String> names = new ArrayList<>();
            try (ResultSet rs = sql.executeQuery(
                    "select c.name from orders o join customer c on c.id = o.customer_id order by o.id")) {
                while (rs.next()) names.add(rs.getString(1));
            }
            assertThat(names).containsExactly("Ann Park", "Ann Park");
        }
    }

    @Test
    void referencingInsteadOfEmbedding_staysCorrectButCostsASecondLookup() {
        DocumentStore customers = new DocumentStore();
        DocumentStore orders = new DocumentStore();
        customers.save("c1", DocumentStore.newDocument().put("_id", "c1").put("name", "Ann Lee"));
        ObjectNode order = order("o1", "c1", null);
        order.remove("customerName");                   // keep only the reference
        orders.save("o1", order);

        customers.save("c1", customers.findById("c1").put("name", "Ann Park"));

        ObjectNode loaded = orders.findById("o1");
        String name = customers.findById(loaded.get("customerId").asText()).get("name").asText();

        assertThat(name).isEqualTo("Ann Park");
        // The application does the join itself: one lookup per collection.
        assertThat(orders.lookups() + customers.lookups()).isEqualTo(3); // 1 for the rename, 2 for the read
    }
}

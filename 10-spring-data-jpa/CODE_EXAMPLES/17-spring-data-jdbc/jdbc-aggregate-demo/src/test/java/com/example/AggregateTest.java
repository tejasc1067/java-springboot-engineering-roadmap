package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

// No test transaction: each repository call commits on its own, like production code outside a service.
@DataJdbcTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AggregateTest {

    @Autowired OrderRepository orders;
    @Autowired CustomerRepository customers;
    @Autowired JdbcTemplate jdbc;

    private Long customerId;

    @BeforeEach
    void clean() {
        orders.deleteAll();
        customers.deleteAll();
        customerId = customers.save(new Customer("Ann")).getId();
    }

    @Test
    void savingTheRoot_savesItsItems() {
        orders.save(new Order(AggregateReference.to(customerId)).addItem("BOOK-1", 2).addItem("PEN-3", 1));

        assertThat(jdbc.queryForObject("select count(*) from order_item", Integer.class)).isEqualTo(2);
    }

    @Test
    void loadedAggregate_isComplete_noLazyLoading() {
        Long id = orders.save(new Order(AggregateReference.to(customerId)).addItem("BOOK-1", 2)).getId();

        // findById has returned and no transaction is open. In JPA, touching a lazy collection now
        // throws LazyInitializationException (topic 14). Here the items were loaded with the order.
        Order loaded = orders.findById(id).orElseThrow();
        assertThat(TestTransaction.isActive()).isFalse();
        assertThat(loaded.getItems()).extracting(OrderItem::getSku).containsExactly("BOOK-1");
    }

    @Test
    void otherAggregates_areReferencedById_notLoaded() {
        Long id = orders.save(new Order(AggregateReference.to(customerId))).getId();

        Order loaded = orders.findById(id).orElseThrow();

        // No order.getCustomer().getName(). You get the id, and load the customer yourself if you need it.
        assertThat(loaded.getCustomerId().getId()).isEqualTo(customerId);
        assertThat(customers.findById(loaded.getCustomerId().getId()).orElseThrow().getName()).isEqualTo("Ann");
    }

    @Test
    void savingTheRoot_rewritesAllItems_fromWhatIsInMemory() {
        Order order = orders.save(new Order(AggregateReference.to(customerId)).addItem("BOOK-1", 2));

        // Meanwhile, someone else adds an item row directly (another process, a support fix, a script).
        jdbc.update("insert into order_item (order_id, line_no, sku, quantity) values (?, 1, 'GIFT-WRAP', 1)",
                order.getId());

        order.setStatus("PAID");     // we only change the status...
        orders.save(order);          // ...but save() deletes ALL the order's items and inserts the ones in memory

        assertThat(jdbc.queryForList("select sku from order_item where order_id = ?", String.class, order.getId()))
                .containsExactly("BOOK-1");   // GIFT-WRAP is gone
    }

    @Test
    void customQuery_isPlainSql() {
        orders.save(new Order(AggregateReference.to(customerId)).addItem("BOOK-1", 1));
        orders.save(new Order(AggregateReference.to(customerId)).addItem("PEN-3", 1));

        assertThat(orders.findContainingSku("BOOK-1")).hasSize(1);
        assertThat(orders.findByStatus("NEW")).hasSize(2);
    }
}

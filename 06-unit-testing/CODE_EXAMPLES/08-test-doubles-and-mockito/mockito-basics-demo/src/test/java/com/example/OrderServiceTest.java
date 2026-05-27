package com.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// MockitoExtension creates the @Mock fields before each test and checks your stubs are used.
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    UserRepository users;     // a fake UserRepository - no real database

    @Mock
    OrderRepository orders;   // a fake OrderRepository

    private OrderService service;

    @BeforeEach
    void setUp() {
        // Build the object under test, passing in the mocks - the same constructor
        // injection from topic 07. The mocks ARE the test doubles.
        service = new OrderService(users, orders);
    }

    @Test
    void placesOrderForAnActiveUser() {
        User ada = new User(1, "Ada", "ada@example.com", true);
        Order order = new Order("A-100", 5000);

        when(users.findById(1)).thenReturn(Optional.of(ada));   // stub: what the lookup returns
        when(orders.save(order)).thenReturn(order);             // stub: what save returns

        Order result = service.placeOrder(1, order);

        assertThat(result).isEqualTo(order);
        verify(orders).save(order);     // verify: the save actually happened
    }

    @Test
    void rejectsAnUnknownUser() {
        when(users.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeOrder(99, new Order("A-1", 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no such user");

        // Nothing should have been saved for a user that doesn't exist.
        verify(orders, never()).save(any());
    }

    @Test
    void rejectsAnInactiveUser() {
        User frozen = new User(2, "Bob", "bob@example.com", false);
        when(users.findById(2)).thenReturn(Optional.of(frozen));

        assertThatThrownBy(() -> service.placeOrder(2, new Order("A-2", 200)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");

        verify(orders, never()).save(any());
    }
}

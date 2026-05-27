package com.example;

/**
 * The class under test. It depends on two collaborators (injected via the constructor,
 * per topic 07). In a unit test, both collaborators are replaced with Mockito mocks so
 * we test only OrderService's own logic - the user check and the save decision.
 */
public class OrderService {

    private final UserRepository users;
    private final OrderRepository orders;

    public OrderService(UserRepository users, OrderRepository orders) {
        this.users = users;
        this.orders = orders;
    }

    public Order placeOrder(long userId, Order order) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("no such user: " + userId));

        if (!user.active()) {
            throw new IllegalStateException("user is not active: " + userId);
        }

        return orders.save(order);
    }
}

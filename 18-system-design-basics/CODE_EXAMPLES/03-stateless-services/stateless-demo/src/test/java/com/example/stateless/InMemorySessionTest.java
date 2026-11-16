package com.example.stateless;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySessionTest {

    @Test
    void cartInEachInstancesMemory_isLostWhenTheNextRequestGoesToTheOtherInstance() {
        CartService a = new CartService("a", new HashMap<>());
        CartService b = new CartService("b", new HashMap<>());
        LoadBalancer<CartService> balancer = LoadBalancer.roundRobin(List.of(a, b));

        balancer.pick("ann").add("ann", "book");      // lands on a
        balancer.pick("ann").add("ann", "lamp");      // lands on b

        assertThat(balancer.pick("ann").cart("ann")).containsExactly("book");   // a: where's the lamp?
        assertThat(balancer.pick("ann").cart("ann")).containsExactly("lamp");   // b: where's the book?
    }

    @Test
    void cartInASharedStore_isTheSameWhicheverInstanceAnswers() {
        var shared = new ConcurrentHashMap<String, List<String>>();
        LoadBalancer<CartService> balancer = LoadBalancer.roundRobin(
                List.of(new CartService("a", shared), new CartService("b", shared)));

        balancer.pick("ann").add("ann", "book");
        balancer.pick("ann").add("ann", "lamp");

        assertThat(balancer.pick("ann").cart("ann")).containsExactly("book", "lamp");
        assertThat(balancer.pick("ann").cart("ann")).containsExactly("book", "lamp");
    }

    @Test
    void withASharedStore_instancesAreDisposable() {
        var shared = new ConcurrentHashMap<String, List<String>>();
        CartService a = new CartService("a", shared);
        CartService b = new CartService("b", shared);
        LoadBalancer<CartService> balancer = LoadBalancer.roundRobin(List.of(a, b));
        balancer.pick("ann").add("ann", "book");

        // Deploy: both old instances replaced by new ones. Nothing they held is missed.
        balancer.remove(a);
        balancer.remove(b);
        balancer.add(new CartService("c", shared));
        balancer.add(new CartService("d", shared));

        assertThat(balancer.pick("ann").cart("ann")).containsExactly("book");
    }
}

package com.example.stateless;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class StickySessionTest {

    @Test
    void stickySessions_hideTheProblem_whileNothingChanges() {
        CartService a = new CartService("a", new HashMap<>());
        CartService b = new CartService("b", new HashMap<>());
        LoadBalancer<CartService> balancer = LoadBalancer.sticky(List.of(a, b));

        balancer.pick("ann").add("ann", "book");
        balancer.pick("ann").add("ann", "lamp");

        assertThat(balancer.pick("ann").cart("ann")).containsExactly("book", "lamp");
    }

    @Test
    void stickySessions_loseEveryCartOnAnInstanceThatGoesAway() {
        CartService a = new CartService("a", new HashMap<>());
        CartService b = new CartService("b", new HashMap<>());
        LoadBalancer<CartService> balancer = LoadBalancer.sticky(List.of(a, b));
        List<String> users = IntStream.range(0, 1_000).mapToObj(i -> "user-" + i).toList();
        users.forEach(u -> balancer.pick(u).add(u, "book"));

        balancer.remove(b);   // a deploy restarts b, or the autoscaler removes it
        long lost = users.stream().filter(u -> balancer.pick(u).cart(u).isEmpty()).count();

        assertThat(lost).isEqualTo(users.stream().filter(u -> b.cart(u).size() == 1).count());   // everyone pinned to b
        assertThat(lost).isBetween(400L, 600L);                                                 // about half the users
    }
}

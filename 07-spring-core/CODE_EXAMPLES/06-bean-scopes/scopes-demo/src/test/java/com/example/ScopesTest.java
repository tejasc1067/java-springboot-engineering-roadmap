package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ScopesTest {

    @Test
    void singletonBeanIsTheSameInstanceAcrossLookups() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            Counter a = ctx.getBean(Counter.class);
            Counter b = ctx.getBean(Counter.class);

            assertThat(a).isSameAs(b);
            a.next();
            assertThat(b.current()).isEqualTo(1);          // mutation visible through the other reference
        }
    }

    @Test
    void prototypeBeanIsADifferentInstancePerLookup() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            WorkTicket a = ctx.getBean(WorkTicket.class);
            WorkTicket b = ctx.getBean(WorkTicket.class);

            assertThat(a).isNotSameAs(b);
            assertThat(a.id()).isNotEqualTo(b.id());
        }
    }

    // Documents the trap. The two calls return the same ticket id even though
    // WorkTicket is prototype-scoped -- the singleton coordinator's dependency was
    // resolved exactly once, at construction.
    @Test
    void brokenCoordinatorReusesTheSameTicket() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            BrokenCoordinator broken = ctx.getBean(BrokenCoordinator.class);

            String first  = broken.startWork();
            String second = broken.startWork();

            assertThat(first).isEqualTo(second);            // same ticket -- the bug
        }
    }

    @Test
    void fixedCoordinatorGetsAFreshTicketEachCall() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            FixedCoordinator fixed = ctx.getBean(FixedCoordinator.class);

            String first  = fixed.startWork();
            String second = fixed.startWork();

            assertThat(first).isNotEqualTo(second);         // different ticket -- the fix
        }
    }
}

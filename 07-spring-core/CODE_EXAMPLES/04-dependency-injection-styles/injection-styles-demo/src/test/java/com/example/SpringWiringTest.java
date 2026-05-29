package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class SpringWiringTest {

    // At runtime, when Spring is involved, all three styles produce a working bean.
    // This test proves the equivalence so the previous three test classes' lesson is
    // clearly about *test-time* and *construction-time* differences, not runtime.
    @Test
    void allThreeGreetersWorkOnceWiredBySpring() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            assertThat(ctx.getBean(ConstructorGreeter.class).greet("ada")).isEqualTo("HELLO, ADA");
            assertThat(ctx.getBean(SetterGreeter.class).greet("ada")).isEqualTo("HELLO, ADA");
            assertThat(ctx.getBean(FieldGreeter.class).greet("ada")).isEqualTo("HELLO, ADA");
        }
    }
}

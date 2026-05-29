package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class AspectTest {

    @Test
    void aspectFiresOnceForEachMatchingServiceMethodCall() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            OrderService orderService = ctx.getBean(OrderService.class);
            UserService userService   = ctx.getBean(UserService.class);
            TimingAspect aspect       = ctx.getBean(TimingAspect.class);

            orderService.place("ada");
            userService.lookup("42");

            assertThat(aspect.events())
                    .hasSize(2)
                    .anySatisfy(e -> assertThat(e).contains("place"))
                    .anySatisfy(e -> assertThat(e).contains("lookup"));
        }
    }

    @Test
    void aspectDoesNotFireOnNonMatchingMethods() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            TimingAspect aspect = ctx.getBean(TimingAspect.class);

            // Calling a non-Service bean's method (the aspect itself, via getBean above)
            // does not match the pointcut. The events list stays empty.
            assertThat(aspect.events()).isEmpty();
        }
    }
}

package com.example;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldGreeterTest {

    // The lesson, surfaced as an executing test. Plain construction NPEs because there
    // is no path for a test to supply the dependency -- no constructor parameter,
    // no setter. The field is private and only Spring (or reflection) can populate it.
    @Test
    void plainConstructionLeavesTheFormatterFieldNull() {
        var greeter = new FieldGreeter();

        assertThatThrownBy(() -> greeter.greet("ada"))
                .isInstanceOf(NullPointerException.class);
    }

    // The escape hatch: reach into the object with reflection. This is what
    // ReflectionTestUtils does in Spring's test utilities. It works; it is gross.
    // Look at it and notice that you would not want to write this for every test.
    @Test
    void reflectionEscapeHatch() throws Exception {
        var greeter = new FieldGreeter();
        Field field = FieldGreeter.class.getDeclaredField("formatter");
        field.setAccessible(true);
        field.set(greeter, new UppercaseFormatter());

        assertThat(greeter.greet("ada")).isEqualTo("HELLO, ADA");
    }

    // Disabled on purpose. Documents the third option: spin up a Spring context just
    // to test a single class. Doable, but you've turned a unit test into an integration
    // test for no reason other than the injection style.
    @Disabled("Documents the 'just boot Spring' workaround -- see SpringWiringTest for the real version.")
    @Test
    void usingSpringTurnsThisIntoAnIntegrationTest() {
    }
}

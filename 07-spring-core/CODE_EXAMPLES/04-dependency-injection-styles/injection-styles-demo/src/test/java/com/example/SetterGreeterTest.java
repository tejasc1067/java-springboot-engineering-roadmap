package com.example;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SetterGreeterTest {

    // It works -- but in two steps, and you have to remember the second one.
    @Test
    void greetsOnceFormatterIsSet() {
        var greeter = new SetterGreeter();
        greeter.setFormatter(new UppercaseFormatter());

        assertThat(greeter.greet("ada")).isEqualTo("HELLO, ADA");
    }

    // The cost of two-step construction: the object is reachable in an invalid state.
    @Test
    void blowsUpIfYouForgetToCallTheSetter() {
        var greeter = new SetterGreeter();

        // Look: a `SetterGreeter` exists that has not been told who its formatter is.
        // The compiler did not catch it. The first method call does.
        assertThatThrownBy(() -> greeter.greet("ada"))
                .isInstanceOf(NullPointerException.class);
    }
}

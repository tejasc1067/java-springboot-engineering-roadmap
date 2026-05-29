package com.example;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConstructorGreeterTest {

    // One line, no Spring, no reflection. The dependency is passed in the way any Java
    // class accepts collaborators. This is the testability win that constructor
    // injection buys you on every test for the lifetime of the class.
    @Test
    void greetsByDelegatingToFormatter() {
        var greeter = new ConstructorGreeter(new UppercaseFormatter());

        assertThat(greeter.greet("ada")).isEqualTo("HELLO, ADA");
    }

    @Test
    void canSwapInAFakeFormatterForFocusedTests() {
        MessageFormatter fixed = input -> "<<" + input + ">>";

        var greeter = new ConstructorGreeter(fixed);

        assertThat(greeter.greet("ada")).isEqualTo("<<Hello, ada>>");
    }
}

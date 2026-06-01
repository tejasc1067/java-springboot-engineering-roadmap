package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "greeter.style=fancy")
class FancyGreeterTest {

    @Autowired
    Greeter greeter;

    @Test
    void fancyGreeterIsWiredWhenStyleIsFancy() {
        assertThat(greeter).isInstanceOf(FancyGreeter.class);
        assertThat(greeter.greet("boot")).isEqualTo("*** GREETINGS, BOOT! ***");
    }
}

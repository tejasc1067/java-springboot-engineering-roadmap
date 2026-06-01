package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DefaultGreeterTest {

    @Autowired
    Greeter greeter;

    @Test
    void plainGreeterIsWiredWhenNoStylePropertySet() {
        assertThat(greeter).isInstanceOf(PlainGreeter.class);
        assertThat(greeter.greet("Boot")).isEqualTo("Hello, Boot.");
    }
}

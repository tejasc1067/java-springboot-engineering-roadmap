package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AppTest {

    @Autowired
    Greeter greeter;

    @Test
    void contextLoadsAndGreeterIsWired() {
        assertThat(greeter).isNotNull();
        assertThat(greeter.greet("Boot")).isEqualTo("Hello, Boot!");
    }
}

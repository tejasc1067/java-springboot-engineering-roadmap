package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DefaultMessageTest {

    @Autowired
    MessagePrinter printer;

    @Test
    void messageComesFromApplicationYml() {
        assertThat(printer.getMessage()).isEqualTo("Default from yml");
    }
}

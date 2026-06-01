package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AppTest {

    @Autowired
    GreetingPrinter printer;

    @Test
    void contextLoadsAndPrinterReadsConfiguredMessage() {
        assertThat(printer).isNotNull();
        assertThat(printer.message()).isEqualTo("Packaged successfully.");
    }
}

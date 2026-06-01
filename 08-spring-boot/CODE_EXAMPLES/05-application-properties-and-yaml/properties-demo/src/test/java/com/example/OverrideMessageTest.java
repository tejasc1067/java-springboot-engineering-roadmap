package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @TestPropertySource adds a property source at a higher precedence than
 * application.yml, mimicking the way command-line args or env vars override
 * file-based config in production.
 */
@SpringBootTest
@TestPropertySource(properties = "app.message=Override from test")
class OverrideMessageTest {

    @Autowired
    MessagePrinter printer;

    @Test
    void testPropertySourceOverridesApplicationYml() {
        assertThat(printer.getMessage()).isEqualTo("Override from test");
    }
}

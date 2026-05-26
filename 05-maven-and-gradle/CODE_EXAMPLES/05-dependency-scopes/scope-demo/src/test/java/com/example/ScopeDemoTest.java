package com.example;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScopeDemoTest {

    // In tests we have access to ALL scopes: compile, test, provided, runtime.
    // That's why this test can import JUnit (test scope).
    @Test
    void slf4jBindingResolvesAtRuntime() {
        var logger = LoggerFactory.getLogger(ScopeDemoTest.class);
        assertNotNull(logger, "SLF4J should pick up a binding from the runtime classpath");

        String impl = logger.getClass().getName();
        assertTrue(impl.toLowerCase().contains("simple"),
                "expected slf4j-simple binding, got: " + impl);
    }
}

package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The default root level is INFO, but Logback's LoggerContext is JVM-global —
 * if another test in this run raised a level, it persists. Pinning the level
 * here keeps the test deterministic regardless of execution order.
 */
@SpringBootTest
@TestPropertySource(properties = "logging.level.com.example=INFO")
@ExtendWith(OutputCaptureExtension.class)
class DefaultLevelTest {

    @Autowired
    LoggingService service;

    @Test
    void infoAndWarnAreLoggedByDefault_debugAndTraceAreNot(CapturedOutput output) {
        service.doWork();

        assertThat(output.getOut())
                .contains("info: the normal operational line")
                .contains("warn: something is off but we continued")
                .doesNotContain("debug: useful when investigating")
                .doesNotContain("trace: very fine-grained detail");
    }
}

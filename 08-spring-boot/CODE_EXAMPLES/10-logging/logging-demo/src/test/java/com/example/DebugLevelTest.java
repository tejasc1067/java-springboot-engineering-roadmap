package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "logging.level.com.example=DEBUG")
@ExtendWith(OutputCaptureExtension.class)
class DebugLevelTest {

    @Autowired
    LoggingService service;

    @Test
    void debugIsLoggedWhenLevelIsRaised(CapturedOutput output) {
        service.doWork();

        assertThat(output.getOut())
                .contains("debug: useful when investigating")
                .contains("info: the normal operational line")
                .doesNotContain("trace: very fine-grained detail");
    }
}

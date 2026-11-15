package com.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.log4j2.Log4J2LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LoggingService is byte-for-byte the class from logging-demo. It talks to SLF4J;
 * only the pom decides whether Logback or Log4j2 writes the line.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class Log4j2SwitchTest {

    @Autowired
    LoggingService service;

    @Test
    void slf4jIsNowBackedByLog4j2() {
        assertThat(LoggerFactory.getILoggerFactory().getClass().getName())
                .startsWith("org.apache.logging.slf4j");
        assertThat(LoggingSystem.get(getClass().getClassLoader()))
                .isInstanceOf(Log4J2LoggingSystem.class);
    }

    @Test
    void sameCodeSameLevelsDifferentImplementation(CapturedOutput output) {
        service.doWork();

        assertThat(output.getOut())
                .contains("info: the normal operational line")
                .doesNotContain("debug: useful when investigating");
    }
}

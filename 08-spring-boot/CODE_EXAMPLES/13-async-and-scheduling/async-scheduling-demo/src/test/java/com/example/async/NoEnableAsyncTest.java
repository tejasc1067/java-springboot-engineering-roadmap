package com.example.async;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

// The same ReportService in a context WITHOUT @EnableAsync.
@SpringBootTest(classes = NoEnableAsyncTest.Config.class)
class NoEnableAsyncTest {

    @Configuration
    @Import(ReportService.class)
    static class Config { }

    @Autowired ReportService reports;

    @Test
    void withoutEnableAsync_theAnnotationIsIgnored() {
        String thread = reports.whoRunsMe().join();

        assertThat(thread).isEqualTo(Thread.currentThread().getName());
    }
}

package com.example.logging;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Spring Boot applies a TaskDecorator bean to the executor behind @Async. This one copies the caller's
 * MDC onto the worker thread for the task, then restores the worker's own afterwards.
 */
@Configuration
@Profile("mdc-propagation")
public class MdcPropagationConfig {

    @Bean
    TaskDecorator mdcTaskDecorator() {
        return task -> {
            Map<String, String> callers = MDC.getCopyOfContextMap();   // runs on the caller's thread
            return () -> {                                             // runs on the worker thread
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (callers != null) MDC.setContextMap(callers); else MDC.clear();
                try {
                    task.run();
                } finally {
                    if (previous != null) MDC.setContextMap(previous); else MDC.clear();
                }
            };
        };
    }
}

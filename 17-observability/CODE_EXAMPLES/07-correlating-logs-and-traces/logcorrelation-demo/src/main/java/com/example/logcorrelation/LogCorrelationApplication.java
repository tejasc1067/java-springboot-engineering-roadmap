package com.example.logcorrelation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
public class LogCorrelationApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogCorrelationApplication.class, args);
    }

    // A pool like many apps create for background work. Nothing copies the trace onto its threads.
    @Bean
    ThreadPoolTaskExecutor plainExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("plain-");
        return executor;
    }

    // The same pool with the decorator from topic 06: the caller's trace goes with each task.
    @Bean
    ThreadPoolTaskExecutor tracedExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("traced-");
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        return executor;
    }
}

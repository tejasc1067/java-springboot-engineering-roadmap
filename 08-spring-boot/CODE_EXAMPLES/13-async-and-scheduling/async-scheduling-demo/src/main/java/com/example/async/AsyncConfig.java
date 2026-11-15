package com.example.async;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Without @EnableAsync, every @Async annotation in the app is silently ignored (see NoEnableAsyncTest).
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    // Where exceptions from void @Async methods end up. The default handler only logs them.
    // A real app would log with context and increment an error metric (module 17).
    public static final List<Throwable> UNCAUGHT = new CopyOnWriteArrayList<>();

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> UNCAUGHT.add(ex);
    }
    // getAsyncExecutor() is not overridden, so @Async uses Spring Boot's auto-configured
    // ThreadPoolTaskExecutor, tuned with spring.task.execution.* in application.yml.
}

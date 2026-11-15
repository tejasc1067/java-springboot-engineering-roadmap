package com.example.async;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class ReportService {

    // Returns which thread ran it, so the tests can see whether it really ran in the background.
    @Async
    public CompletableFuture<String> whoRunsMe() {
        return CompletableFuture.completedFuture(Thread.currentThread().getName());
    }

    // Calls the @Async method on `this` - the real object, not the Spring proxy. @Async is skipped.
    public String whoRunsMeViaSelfCall() {
        return whoRunsMe().join();
    }

    // void: the caller has already returned by the time this throws. Nobody can catch it.
    @Async
    public void failInTheBackground() {
        throw new IllegalStateException("report generation failed");
    }

    // Returning a future gives the exception somewhere to go.
    @Async
    public CompletableFuture<Void> failVisibly() {
        throw new IllegalStateException("report generation failed");
    }
}

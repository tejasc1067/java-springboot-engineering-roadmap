package com.example.async;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AsyncTest {

    @Autowired ReportService reports;
    @Autowired ReportCaller caller;
    @Autowired ApplicationEventPublisher events;
    @Autowired ConfirmationEmailListener emailListener;

    private final String testThread = Thread.currentThread().getName();

    @BeforeEach
    void clear() { AsyncConfig.UNCAUGHT.clear(); }

    @Test
    void asyncMethod_runsOnBootsExecutor() {
        String thread = reports.whoRunsMe().join();

        assertThat(thread).startsWith("report-").isNotEqualTo(testThread);
    }

    @Test
    void selfInvocation_skipsTheProxy_andRunsOnTheCallersThread() {
        String thread = reports.whoRunsMeViaSelfCall();

        assertThat(thread).isEqualTo(testThread);   // @Async silently did nothing
    }

    @Test
    void callingFromAnotherBean_goesThroughTheProxy() {
        assertThat(caller.whoRunsTheReport()).startsWith("report-");
    }

    @Test
    void exceptionFromVoidAsync_neverReachesTheCaller() throws Exception {
        reports.failInTheBackground();          // returns normally: the work hasn't even started yet

        // The exception happens later, on another thread, and goes to the AsyncUncaughtExceptionHandler.
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (AsyncConfig.UNCAUGHT.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(AsyncConfig.UNCAUGHT).singleElement()
                .extracting(Throwable::getMessage).isEqualTo("report generation failed");
    }

    @Test
    void exceptionFromFutureAsync_reachesTheCallerThroughTheFuture() {
        assertThatThrownBy(() -> reports.failVisibly().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(AsyncConfig.UNCAUGHT).isEmpty();
    }

    @Test
    void asyncEventListener_runsOffThePublishersThread() {
        events.publishEvent(new OrderPlaced("order-1"));

        assertThat(emailListener.ranOn().join()).startsWith("report-");
    }
}

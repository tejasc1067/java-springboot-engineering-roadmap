package com.example.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A web server reuses a small pool of threads for every request. One thread is enough to show
 * what a missing MDC.remove does: the next request on that thread inherits the last one's user.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class MdcLeakTest {

    @Autowired RequestHandler handler;
    private final ExecutorService serverThread = Executors.newSingleThreadExecutor();

    @AfterEach
    void stop() throws Exception {
        serverThread.submit(MDC::clear).get();
        serverThread.shutdownNow();
    }

    @Test
    void withoutClearing_theNextRequestOnTheSameThreadIsLoggedAsTheLastUser(CapturedOutput output) throws Exception {
        serverThread.submit(() -> handler.handleBroken("ann", "/orders")).get();
        serverThread.submit(() -> handler.handleBroken(null, "/public/catalog")).get();   // anonymous request

        assertThat(JsonLines.withMessage(output, "handling /public/catalog").get("userId").asText()).isEqualTo("ann");
    }

    @Test
    void clearingInFinally_theNextRequestStartsClean(CapturedOutput output) throws Exception {
        serverThread.submit(() -> handler.handle("ann", "/orders")).get();
        serverThread.submit(() -> handler.handle(null, "/public/catalog")).get();

        assertThat(JsonLines.withMessage(output, "handling /orders").get("userId").asText()).isEqualTo("ann");
        assertThat(JsonLines.withMessage(output, "handling /public/catalog").has("userId")).isFalse();
    }
}

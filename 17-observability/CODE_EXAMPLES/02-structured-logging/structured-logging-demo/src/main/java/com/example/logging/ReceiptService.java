package com.example.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    /** Runs on a "task-N" thread. The MDC is a ThreadLocal, so it holds whatever that thread has. */
    @Async
    public CompletableFuture<Void> sendReceipt(String orderId) {
        log.info("receipt sent for {}", orderId);
        return CompletableFuture.completedFuture(null);
    }
}

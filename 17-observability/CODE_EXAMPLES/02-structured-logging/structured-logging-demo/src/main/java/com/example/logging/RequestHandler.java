package com.example.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** What a servlet filter does for every request: put who's calling into the MDC, run the request. */
@Component
public class RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    /** Broken: puts the user into the MDC and never takes it out. */
    public void handleBroken(String userId, String path) {
        if (userId != null) MDC.put("userId", userId);
        log.info("handling {}", path);
    }

    /** Fixed: the MDC entry lives exactly as long as the request. */
    public void handle(String userId, String path) {
        if (userId != null) MDC.put("userId", userId);
        try {
            log.info("handling {}", path);
        } finally {
            MDC.remove("userId");
        }
    }
}

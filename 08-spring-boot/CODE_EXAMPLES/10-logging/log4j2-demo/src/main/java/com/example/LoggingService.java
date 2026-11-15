package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingService {

    private static final Logger log = LoggerFactory.getLogger(LoggingService.class);

    public void doWork() {
        log.trace("trace: very fine-grained detail");
        log.debug("debug: useful when investigating");
        log.info("info: the normal operational line");
        log.warn("warn: something is off but we continued");
    }
}

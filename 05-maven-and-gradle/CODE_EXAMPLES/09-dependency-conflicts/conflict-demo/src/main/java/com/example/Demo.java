package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Demo {
    public static void main(String[] args) {
        Logger log = LoggerFactory.getLogger(Demo.class);

        String slf4jApiVersion = Logger.class.getPackage().getImplementationVersion();
        log.info("slf4j-api effective at runtime: {}", slf4jApiVersion);
        log.info("Logger implementation class: {}", log.getClass().getName());
    }
}

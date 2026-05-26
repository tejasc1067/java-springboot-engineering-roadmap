package com.example;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Note what we CAN import here:
//   slf4j-api          -> compile scope, allowed
//   jakarta.servlet    -> provided scope, allowed at compile time
//
// What we CAN'T import here:
//   slf4j-simple       -> runtime scope, no compile-time visibility
//   junit-jupiter      -> test scope, no compile-time visibility
//
// If you uncomment the next line, `mvn compile` fails:
// import org.junit.jupiter.api.Test;
public class ScopeDemo {

    private static final Logger log = LoggerFactory.getLogger(ScopeDemo.class);

    public static void main(String[] args) {
        log.info("ScopeDemo running, logging via SLF4J binding discovered at runtime");
    }

    public String describe(HttpServletRequest request) {
        // We compile against the servlet API, but in a fat-jar deployment
        // the container (Tomcat, Jetty) would supply the actual classes.
        return "method=" + request.getMethod() + " uri=" + request.getRequestURI();
    }
}

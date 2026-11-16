package com.example.network;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@RestController
public class NetworkApplication {

    @Value("${app.name}")
    private String name;

    // Where the OTHER service lives, as seen from inside THIS container. demo.sh sets it with OTHER_URL.
    @Value("${other.url}")
    private String otherUrl;

    @GetMapping("/hello")
    String hello() { return "hello from " + name; }

    // Calls the other service and reports what happened, including the failure.
    @GetMapping("/call-other")
    String callOther() {
        try {
            return RestClient.create().get().uri(otherUrl + "/hello").retrieve().body(String.class);
        } catch (Exception e) {
            // The useful part is the chain of causes, e.g. ResourceAccessException <- ConnectException.
            StringBuilder chain = new StringBuilder(e.getClass().getSimpleName());
            for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
                chain.append(" <- ").append(cause.getClass().getSimpleName());
            }
            return "FAILED calling " + otherUrl + ": " + chain;
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(NetworkApplication.class, args);
    }
}

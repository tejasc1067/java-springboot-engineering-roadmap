package com.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

@SpringBootApplication
@RestController
public class ConfigApplication {

    private final Environment env;

    @Value("${app.greeting}")
    private String greeting;

    @Value("${app.mode}")
    private String mode;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    public ConfigApplication(Environment env) { this.env = env; }

    // Reports what configuration the app received. It says whether the secret is set, never what it is.
    @GetMapping("/config")
    Map<String, Object> config() {
        return Map.of(
                "profiles", Arrays.toString(env.getActiveProfiles()),
                "mode", mode,
                "greeting", greeting,
                "dbPasswordSet", !dbPassword.isEmpty());
    }

    public static void main(String[] args) {
        SpringApplication.run(ConfigApplication.class, args);
    }
}

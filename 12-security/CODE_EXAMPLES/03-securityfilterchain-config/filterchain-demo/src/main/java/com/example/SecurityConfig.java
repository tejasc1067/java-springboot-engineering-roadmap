package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

// Defining our own SecurityFilterChain bean makes Spring Boot back off its auto-configured default chain
// (topic 02). Now WE decide what's open, what's protected, and how credentials are supplied.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Rules are evaluated top-to-bottom and the FIRST match wins, so the specific,
                        // open route must come before the catch-all. Swap these two lines and /public/**
                        // would be caught by anyRequest() and start demanding a login.
                        .requestMatchers("/public/**").permitAll()
                        // anyRequest() is the catch-all and MUST be last — Spring throws at startup if any
                        // requestMatcher is declared after it. Everything not matched above needs a login.
                        .anyRequest().authenticated())
                // Enable HTTP Basic so a caller can actually supply credentials. We deliberately do NOT
                // enable form login here (that, and the Basic-vs-form-vs-token choice, is topic 06), so an
                // unauthenticated caller always gets a clean 401 rather than a redirect to a login page.
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}

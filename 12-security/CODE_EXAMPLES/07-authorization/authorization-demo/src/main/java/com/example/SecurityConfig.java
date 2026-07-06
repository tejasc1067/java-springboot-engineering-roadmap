package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

// @EnableMethodSecurity turns on the @PreAuthorize/@PostAuthorize annotations used in BookController.
// It complements — does not replace — the URL rules below.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // URL-based authorization: coarse, by path. Everything under /api/admin needs ADMIN.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Everything else just needs a logged-in user; finer rules live on the methods.
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // Disabled so DELETE is callable without a CSRF token in this API-style demo. Topic 11
                // explains when disabling CSRF is correct (stateless/token APIs) and when it's a bug.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    // Two users with different roles. .roles("ADMIN") grants the authority "ROLE_ADMIN" — the ROLE_ prefix
    // is what hasRole("ADMIN") checks for. That prefix convention is the whole "roles vs authorities" story.
    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        UserDetails alice = User.withUsername("alice")
                .password(encoder.encode("password"))
                .roles("USER")            // -> authority ROLE_USER
                .build();
        UserDetails admin = User.withUsername("admin")
                .password(encoder.encode("password"))
                .roles("ADMIN")           // -> authority ROLE_ADMIN
                .build();
        return new InMemoryUserDetailsManager(alice, admin);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

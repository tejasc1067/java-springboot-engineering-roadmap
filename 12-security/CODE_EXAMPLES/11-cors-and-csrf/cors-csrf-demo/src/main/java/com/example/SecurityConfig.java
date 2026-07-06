package com.example;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// This is a COOKIE/SESSION app (form login -> JSESSIONID), so CSRF protection is REQUIRED and left ON — the
// deliberate opposite of topics 08-09, where auth was a bearer token in a header and disabling CSRF was
// correct. CORS is configured to let one specific browser origin (a SPA) call this API.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error").permitAll()
                        .anyRequest().authenticated())
                // Turn on CORS using the CorsConfigurationSource bean below. Without this, the browser blocks
                // the SPA from reading responses cross-origin (the "CORS error" everyone hits).
                .cors(Customizer.withDefaults())
                // CSRF stays ON. CookieCsrfTokenRepository.withHttpOnlyFalse() puts the token in an XSRF-TOKEN
                // cookie that the SPA's JavaScript can read and echo back in the X-XSRF-TOKEN header — the
                // standard pattern for a cookie-authenticated single-page app.
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .formLogin(Customizer.withDefaults());
        return http.build();
    }

    // CORS is about which OTHER browser origins may read this API's responses. It is NOT access control:
    // a non-browser client (curl, another server) ignores it entirely. Note allowCredentials(true) forces an
    // EXACT origin — the spec forbids pairing credentials with "*", and Spring will reject that combination.
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));   // the SPA origin — explicit, never "*"
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);                              // permit the session cookie cross-origin
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        UserDetails alice = User.withUsername("alice")
                .password(encoder.encode("password"))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(alice);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

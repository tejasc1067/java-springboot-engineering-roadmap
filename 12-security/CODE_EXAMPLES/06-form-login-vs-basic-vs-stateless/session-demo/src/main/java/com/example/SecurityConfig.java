package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

// Two SecurityFilterChains showing the two ends of the session axis, each using the credential mechanism
// that fits it. Lowest @Order wins, so the specific /stateless/** chain is matched before the catch-all.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // STATELESS (HTTP Basic). Credentials ride on EVERY request; SessionCreationPolicy.STATELESS means no
    // HttpSession is ever created, so nothing is remembered server-side. This is the token-auth model
    // (JWT, topics 08-10). Note: in Spring Security 6, HTTP Basic is already stateless by default — the
    // STATELESS policy just makes the intent explicit and guarantees no other filter creates a session.
    @Bean
    @Order(1)
    SecurityFilterChain statelessChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/stateless/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // No session means no session-riding attack to defend, so CSRF is off here. Topic 11
                // explains exactly when disabling CSRF is correct and when it's a serious bug.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    // STATEFUL (form login). The user posts credentials once to /login; Spring authenticates them, stores
    // the result in an HttpSession, and hands back a JSESSIONID cookie. Every later request is authenticated
    // by that cookie — the credentials are not sent again. This is the classic server-rendered web-app model.
    @Bean
    @Order(2)
    SecurityFilterChain statefulChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .formLogin(Customizer.withDefaults());   // default /login page + session-backed context
        return http.build();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        UserDetails alice = User.withUsername("alice")
                .password(encoder.encode("password"))   // BCrypt, per topic 05
                .authorities("ROLE_USER")
                .build();
        return new InMemoryUserDetailsManager(alice);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

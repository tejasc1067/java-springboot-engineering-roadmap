package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

// The app is an OAuth2 CLIENT here, not a resource server. oauth2Login() turns an unauthenticated request to
// a protected page into a redirect to the provider (Google), handles the callback, exchanges the code for
// tokens, builds the user, and — crucially — establishes a SERVER-SIDE SESSION. So unlike topics 08-09
// (stateless bearer tokens, CSRF off), this is a stateful browser app: sessions are used and CSRF stays ON.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error").permitAll()
                        // The flow endpoints (/oauth2/authorization/**, /login/**) are permitted by
                        // oauth2Login itself — you must be able to reach them before you're logged in.
                        .anyRequest().authenticated())
                // One line: the entire authorization-code flow. With a single registration, an unauthenticated
                // request to a protected route is sent to /oauth2/authorization/google, which redirects to
                // Google's consent screen. After consent, Google calls back to /login/oauth2/code/google.
                .oauth2Login(Customizer.withDefaults());
        // No sessionManagement(STATELESS) and no csrf(disable): this is the deliberate opposite of the token
        // topics. Login produces a JSESSIONID; the session is what keeps you logged in on later requests.
        return http.build();
    }
}

package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        JwtAuthFilter jwtAuthFilter = new JwtAuthFilter(jwtDecoder);
        http
                .authorizeHttpRequests(auth -> auth
                        // /auth/login must be open — you can't hold a token before you log in.
                        .requestMatchers("/public/**", "/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // Pure token auth: no server-side session, so nothing to remember between requests. Every
                // request must carry its own bearer token. This is what "stateless" means (topic 06).
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // No cookie/session means no CSRF vector, so CSRF protection is correctly off here (topic 11).
                .csrf(csrf -> csrf.disable())
                // No form login and no HTTP Basic: the ONLY way to authenticate is a bearer token, checked by
                // our filter. We run it before the username/password filter's slot in the chain.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        // Unauthenticated on a protected route -> clean 401 (no redirect to a login page).
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // Authenticated but wrong role -> 403. We set the status directly instead of the
                        // default handler's response.sendError(): sendError makes the servlet container
                        // re-dispatch to /error, which re-enters this chain as ANONYMOUS (our filter doesn't
                        // re-run on error dispatch) and the 403 gets overwritten by a 401. setStatus avoids
                        // that dispatch entirely — a token API returns bare status codes, no error page.
                        // Topic 12 replaces both of these lambdas with a ProblemDetail body.
                        .accessDeniedHandler((request, response, ex) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value())));
        return http.build();
    }

    // Same in-memory users and BCrypt store as topics 05-07. These back the /auth/login credential check;
    // once a token is issued, the store is not consulted again — the token carries everything.
    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        UserDetails alice = User.withUsername("alice")
                .password(encoder.encode("password"))
                .roles("USER")            // -> authority ROLE_USER, carried in the token's "roles" claim
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

    // Exposes the AuthenticationManager that /auth/login uses to verify the username/password ONCE, before
    // minting a token. It is the same DaoAuthenticationProvider (UserDetailsService + PasswordEncoder) from
    // topic 04 — the token model reuses the password model, it does not replace it.
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

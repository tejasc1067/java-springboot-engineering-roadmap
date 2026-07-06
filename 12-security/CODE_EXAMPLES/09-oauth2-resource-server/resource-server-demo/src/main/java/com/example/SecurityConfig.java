package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// Compare this whole file to topic 08. There is NO JwtConfig (no key we own — we only verify), NO JwtService
// (we never issue tokens), and NO hand-written JwtAuthFilter. oauth2ResourceServer(...) installs the
// framework's BearerTokenAuthenticationFilter and wires proper 401/403 bearer-token handlers for us. The
// decoder is built from a PUBLIC key only (see application.yml) — this app literally cannot mint a token.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public/**").permitAll()
                        // Authorization by SCOPE, the OAuth2-native primitive. The IdP puts a "scope" claim in
                        // the token; Spring maps each scope to a "SCOPE_<name>" authority automatically. This
                        // is the difference from topic 07/08's roles: scopes describe what the TOKEN may do.
                        .requestMatchers(HttpMethod.GET, "/api/books/**").hasAuthority("SCOPE_books:read")
                        .requestMatchers(HttpMethod.DELETE, "/api/books/**").hasAuthority("SCOPE_books:write")
                        .anyRequest().authenticated())
                // Validate the incoming bearer token as a JWT. Customizer.withDefaults() uses the decoder
                // auto-configured from application.yml. This single line replaces topic 08's entire filter.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // A resource server is stateless by definition: each request re-presents its token.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        // No custom exceptionHandling needed: the resource-server handlers already return a clean 401
        // (with a WWW-Authenticate header) and a 403 via setStatus — so unlike topic 08 there is no
        // sendError -> /error re-dispatch turning the 403 into a 401.
        return http.build();
    }
}

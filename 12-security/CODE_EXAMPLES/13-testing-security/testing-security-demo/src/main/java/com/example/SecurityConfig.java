package com.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

// A small secured app that gives the tests something to assert against: URL rules, method security, and a
// CSRF-protected POST. HTTP Basic is browser-auto-sent, so per topic 11 CSRF stays ON here — which also lets
// the tests demonstrate .with(csrf()).
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // enables @PreAuthorize on BookController.delete
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // Clean 401/403 via setStatus (not sendError), so MockMvc and a real curl AGREE on the 403.
                // This is deliberate: it removes the topic-08 /error-redispatch trap, which topic 13's markdown
                // then discusses as the classic case where MockMvc's result differs from the real server.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, denied) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value())));
        return http.build();
    }

    // Real BCrypt-encoded users. Tests that send real credentials (.with(httpBasic(...))) exercise this whole
    // chain — UserDetailsService + PasswordEncoder — which @WithMockUser deliberately skips.
    @Bean
    UserDetailsService users(PasswordEncoder encoder) {
        UserDetails alice = User.withUsername("alice").password(encoder.encode("password")).roles("USER").build();
        UserDetails admin = User.withUsername("admin").password(encoder.encode("password")).roles("ADMIN").build();
        return new InMemoryUserDetailsManager(alice, admin);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

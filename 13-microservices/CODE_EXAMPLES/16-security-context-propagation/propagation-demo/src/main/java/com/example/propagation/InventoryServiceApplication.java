package com.example.propagation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The DOWNSTREAM (inventory-service), secured as an OAuth2 resource server: every request
 * must carry a valid JWT, or it's rejected with 401. Because the endpoint reports the
 * authenticated subject, the demo can show that the caller's identity actually TRAVELLED
 * across the hop — not just that the call succeeded.
 */
@SpringBootApplication
@RestController
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .csrf(csrf -> csrf.disable()); // stateless bearer-token API (module 12, topic 11)
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        // Validate HS256 tokens signed with the shared demo secret. Production: decode
        // against the IdP's JWK set (NimbusJwtDecoder.withJwkSetUri(...)).
        return NimbusJwtDecoder.withSecretKey(DemoKeys.HMAC).build();
    }

    @GetMapping("/api/inventory/{sku}")
    public String getStock(@PathVariable String sku, JwtAuthenticationToken auth) {
        // auth.getName() is the JWT subject — proof the downstream knows WHO is calling.
        return "{\"sku\":\"" + sku + "\",\"available\":5,\"caller\":\"" + auth.getName() + "\"}";
    }
}

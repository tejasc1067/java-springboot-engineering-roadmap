package com.example;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

// This file IS the "self-signed" in self-signed JWT: one symmetric secret produces BOTH the encoder that
// signs tokens and the decoder that verifies them. That only works because the SAME app issues and validates.
// When someone else issues the token (topic 09), you can't share their secret — that is exactly why real
// identity providers sign with a PRIVATE key and publish a PUBLIC one (asymmetric), so verifiers need no secret.
@Configuration
public class JwtConfig {

    // The signing secret. HS256 (HMAC-SHA256) requires at least 256 bits = 32 bytes of key material.
    // Read from the DEMO_JWT_SECRET environment variable; the literal after ':' is a LOCAL-ONLY fallback.
    // In production this is loaded from the environment or a secret manager and is NEVER committed to source
    // control — a leaked signing secret lets anyone forge tokens for any user (see COMMON_MISTAKES).
    @Bean
    SecretKey jwtSecretKey(@Value("${demo.jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("demo.jwt.secret must be at least 32 bytes for HS256");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    // Signs claims into a compact JWT string. NimbusJwtEncoder is the exact code the framework uses internally.
    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
    }

    // Verifies a token's signature (was it signed with THIS secret?) and its standard claims (is it expired?).
    // decode(...) throws JwtException if either check fails — that is what stops a tampered or expired token.
    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}

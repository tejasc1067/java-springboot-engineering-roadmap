package com.example;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.InputStream;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.converter.RsaKeyConverters;

// Stands in for the AUTHORIZATION SERVER (a real IdP like Keycloak). It holds the PRIVATE key — which is why
// it lives in the test tree and app.key is a test resource: the resource server under test never sees it.
// Everything here is what the IdP does; the app only ever verifies the result with the matching public key.
final class TokenIssuer {

    private TokenIssuer() {
    }

    // Loads the private key that matches src/main/resources/app.pub (the app's public key).
    private static RSAPrivateKey privateKey() throws Exception {
        try (InputStream in = new ClassPathResource("app.key").getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(in);
        }
    }

    private static String sign(RSAPrivateKey key, String subject, String scope, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://demo-authserver.example")
                .subject(subject)
                .claim("scope", scope)          // space-delimited; Spring maps each to a SCOPE_* authority
                .issueTime(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(expiresAt))
                .build();
        JWSSigner signer = new RSASSASigner(key);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(signer);              // RS256: signed with the PRIVATE key, verifiable with the PUBLIC key
        return jwt.serialize();
    }

    // A normal, valid token: correct key, one-hour lifetime.
    static String issue(String subject, String scope) throws Exception {
        return sign(privateKey(), subject, scope, Instant.now().plus(1, ChronoUnit.HOURS));
    }

    // Correctly signed but already expired — proves the decoder enforces exp, not just the signature.
    static String issueExpired(String subject, String scope) throws Exception {
        return sign(privateKey(), subject, scope, Instant.now().minus(1, ChronoUnit.HOURS));
    }

    // Signed with a DIFFERENT key the app has never seen. Models a forged token: the app's public key can't
    // verify it, so it is rejected. This is the asymmetric integrity guarantee.
    static String issueWithForeignKey(String subject, String scope) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        RSAPrivateKey foreign = (RSAPrivateKey) gen.generateKeyPair().getPrivate();
        return sign(foreign, subject, scope, Instant.now().plus(1, ChronoUnit.HOURS));
    }
}

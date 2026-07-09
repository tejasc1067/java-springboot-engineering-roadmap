package com.example.propagation;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Boots the secured inventory-service and calls it two ways: with no token (rejected)
 * and via a client that PROPAGATES the caller's token (accepted, and the downstream
 * reports the caller's identity). This is the whole lesson end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenPropagationEndToEndTest {

    @LocalServerPort
    int port;

    private String url() {
        return "http://localhost:" + port + "/api/inventory/SKU-BOOK";
    }

    @Test
    void withoutATokenTheDownstreamRejects() {
        RestClient noToken = RestClient.create();

        // Identity didn't travel — the secured downstream returns 401.
        assertThatThrownBy(() -> noToken.get().uri(url()).retrieve().body(String.class))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    void propagatingTheTokenLetsTheCallThroughAndIdentityTravels() throws Exception {
        String callerToken = mintToken("alice");

        // A client that carries the caller's token on its outbound request.
        RestClient propagating = RestClient.builder()
                .requestInterceptor(new BearerTokenPropagationInterceptor(() -> "Bearer " + callerToken))
                .build();

        String body = propagating.get().uri(url()).retrieve().body(String.class);

        assertThat(body).contains("SKU-BOOK");
        assertThat(body).contains("\"caller\":\"alice\""); // the downstream knows WHO called
    }

    /** Mints an HS256 JWT signed with the shared demo secret (stands in for the IdP). */
    private static String mintToken(String subject) throws Exception {
        JWSSigner signer = new MACSigner(DemoKeys.HMAC.getEncoded());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(signer);
        return jwt.serialize();
    }
}

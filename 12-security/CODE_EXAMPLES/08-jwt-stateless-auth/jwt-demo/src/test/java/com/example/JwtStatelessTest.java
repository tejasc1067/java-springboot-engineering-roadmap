package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

// The full self-signed loop, tested end to end: log in to GET a token, then use it. The last two tests are
// the ones that matter most — they prove what the signature and the exp claim actually buy you.
@SpringBootTest
@AutoConfigureMockMvc
class JwtStatelessTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    // Used only to forge an expired token; the app itself never issues one with a past exp.
    @Autowired
    JwtEncoder jwtEncoder;

    private String login(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new AuthController.LoginRequest(username, password));
        String json = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("token").asText();
    }

    @Test
    void publicRouteNeedsNoToken() throws Exception {
        mvc.perform(get("/public/hello")).andExpect(status().isOk());
    }

    @Test
    void protectedRouteWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordYieldsNoToken401() throws Exception {
        String body = objectMapper.writeValueAsString(new AuthController.LoginRequest("alice", "wrong"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenCarriesIdentityAndAuthorities() throws Exception {
        String token = login("alice", "password");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("alice"))
                // The authority came out of the token's "roles" claim, not a session lookup.
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
    }

    @Test
    void adminTokenReachesAdminArea() throws Exception {
        String token = login("admin", "password");
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void userTokenIsForbiddenFromAdminArea403() throws Exception {
        // alice is authenticated by a valid token (passes the entry point) but her token carries only
        // ROLE_USER, so the hasRole('ADMIN') URL rule denies -> 403, not 401.
        String token = login("alice", "password");
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void tamperedTokenIsRejected401() throws Exception {
        // The attacker holds a valid ROLE_USER token and edits the payload (imagine flipping a claim to
        // escalate privileges). The signature was computed over the ORIGINAL payload, so verification fails.
        String token = login("alice", "password");
        String[] parts = token.split("\\.");
        char c = parts[1].charAt(3);
        char flipped = (c == 'a') ? 'b' : 'a';
        String tamperedPayload = parts[1].substring(0, 3) + flipped + parts[1].substring(4);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected401() throws Exception {
        // A correctly-signed token whose exp is in the past. The signature is valid; the timestamp check is
        // what rejects it. This is why a stolen token is only useful until it expires.
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("jwt-demo")
                .issuedAt(now.minus(2, ChronoUnit.HOURS))
                .expiresAt(now.minus(1, ChronoUnit.HOURS))
                .subject("alice")
                .claim("roles", "ROLE_USER")
                .build();
        String expired = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturnsANonEmptyToken() throws Exception {
        String token = login("alice", "password");
        // A JWT is three base64url segments separated by dots: header.payload.signature.
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }
}

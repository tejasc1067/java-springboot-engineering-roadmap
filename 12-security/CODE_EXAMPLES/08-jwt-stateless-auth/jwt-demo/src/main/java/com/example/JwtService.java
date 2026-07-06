package com.example;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

// Turns a verified Authentication (the result of a successful username/password check) into a signed JWT.
// The claims are the whole point of "stateless": everything the server later needs about this caller —
// who they are (sub) and what they can do (roles) — is carried IN the token, so no session is stored.
@Service
public class JwtService {

    private final JwtEncoder encoder;

    JwtService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    String issue(Authentication authentication) {
        Instant now = Instant.now();
        // Authorities like "ROLE_ADMIN" are flattened into one space-separated string. The custom filter
        // splits this claim back into authorities on the way in (JwtAuthFilter).
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("jwt-demo")               // who minted it
                .issuedAt(now)                     // iat
                .expiresAt(now.plus(1, ChronoUnit.HOURS))  // exp — after this the decoder rejects it
                .subject(authentication.getName()) // sub — the username
                .claim("roles", roles)             // custom claim carrying authorities
                .build();

        // HS256 must be named explicitly in the header; the decoder is pinned to HS256 to match.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}

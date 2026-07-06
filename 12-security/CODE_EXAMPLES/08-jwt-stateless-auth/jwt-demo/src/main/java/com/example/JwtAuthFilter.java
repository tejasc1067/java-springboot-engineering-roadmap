package com.example;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

// This filter is the WHOLE job of validating a bearer token, done by hand so you can see it: read the
// Authorization header, verify the token, and if it holds up, tell Spring Security who the caller is by
// putting an Authentication into the SecurityContext. Topic 09 deletes this entire class — oauth2ResourceServer
// installs an equivalent filter for you. Seeing it here is what makes topic 09's one-liner make sense.
//
// Deliberately NOT a @Component: Spring Boot auto-registers any Filter bean with the servlet container, which
// would run it a second time outside the security chain. We construct it in SecurityConfig instead.
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtDecoder decoder;

    public JwtAuthFilter(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            try {
                // decode() does the security-critical work: recompute the signature over header.payload and
                // compare it to the token's signature, and check exp. A tampered payload (e.g. the caller
                // edited "roles" to sneak in ROLE_ADMIN) or an expired token throws here.
                Jwt jwt = decoder.decode(token);

                String username = jwt.getSubject();
                String roles = jwt.getClaimAsString("roles");
                List<SimpleGrantedAuthority> authorities = (roles == null || roles.isBlank())
                        ? List.of()
                        : Arrays.stream(roles.split(" ")).map(SimpleGrantedAuthority::new).toList();

                // Credentials are null: there is no password to carry — the signed token IS the proof.
                var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException e) {
                // Invalid/tampered/expired token: leave the context empty. Downstream, the endpoint is
                // protected, so the AuthenticationEntryPoint fires and the caller gets 401 — never a 500.
                SecurityContextHolder.clearContext();
            }
        }
        // No header, or a bad token, simply means "no authenticated user" — public routes still work,
        // protected routes will be rejected by the authorization rules further down the chain.
        chain.doFilter(request, response);
    }
}

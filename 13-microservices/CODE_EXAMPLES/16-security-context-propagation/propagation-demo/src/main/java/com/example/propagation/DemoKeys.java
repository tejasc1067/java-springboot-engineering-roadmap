package com.example.propagation;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * DEMO ONLY. A hardcoded HMAC secret so the resource server (which validates tokens)
 * and the test (which mints them) agree on a key, with no external identity provider.
 * Real systems never hardcode signing keys and use asymmetric keys validated against
 * an IdP's published JWK set (module 12, topic 09). The propagation lesson is identical
 * either way — only the key management differs.
 */
public final class DemoKeys {

    public static final SecretKey HMAC = new SecretKeySpec(
            "demo-secret-key-32-bytes-long-1234567890".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private DemoKeys() {
    }
}

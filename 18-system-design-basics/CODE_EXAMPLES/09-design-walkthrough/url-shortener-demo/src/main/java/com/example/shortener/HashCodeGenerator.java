package com.example.shortener;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hash the URL and keep only enough of it for a short code. Same URL, same code, but truncation means collisions. */
public class HashCodeGenerator implements CodeGenerator {

    private final int length;

    public HashCodeGenerator(int length) {
        this.length = length;
    }

    @Override
    public String generate(String longUrl) {
        try {
            byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(longUrl.getBytes(StandardCharsets.UTF_8));
            long first8Bytes = ByteBuffer.wrap(sha256).getLong();
            // 256 bits squeezed into 62^length values: different URLs must eventually share a code.
            return Base62.padded(Math.floorMod(first8Bytes, Base62.capacity(length)), length);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

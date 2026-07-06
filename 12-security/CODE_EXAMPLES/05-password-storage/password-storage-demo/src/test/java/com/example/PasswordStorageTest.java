package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// Asserts the security properties the demo classes narrate: an unsalted fast hash is deterministic (so it
// leaks shared passwords) AND crackable by dictionary, while BCrypt is salted, still verifiable, and
// correctly formatted.
class PasswordStorageTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void unsaltedHashIsDeterministic_soItLeaksSharedPasswords() {
        // Same input -> same hash means two users with the same password are visibly identical in a dump.
        assertThat(sha256("correcthorse")).isEqualTo(sha256("correcthorse"));
    }

    @Test
    void fastHashIsCrackedByADictionary() {
        String leaked = sha256("hunter2");
        String[] dictionary = {"123456", "password", "qwerty", "hunter2", "letmein"};

        String cracked = null;
        for (String guess : dictionary) {
            if (sha256(guess).equals(leaked)) {
                cracked = guess;
                break;
            }
        }
        assertThat(cracked).isEqualTo("hunter2");   // the attack succeeds
    }

    @Test
    void bcryptSalts_soIdenticalPasswordsProduceDifferentHashes() {
        assertThat(encoder.encode("correcthorse"))
                .isNotEqualTo(encoder.encode("correcthorse"));
    }

    @Test
    void bcryptStillVerifiesTheOriginalPassword() {
        String hash = encoder.encode("correcthorse");
        assertThat(encoder.matches("correcthorse", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }

    @Test
    void bcryptHashHasTheExpectedFormat() {
        // $2a$ = bcrypt identifier, 10 = default work factor (cost).
        assertThat(encoder.encode("x")).startsWith("$2a$10$");
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

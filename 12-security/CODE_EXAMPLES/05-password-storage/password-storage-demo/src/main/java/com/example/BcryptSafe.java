package com.example;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// THE FIX. BCrypt is a password hashing function designed for exactly this job. It fixes both problems
// from FastHashVulnerable:
//   1) It generates a random SALT per hash and embeds it, so the SAME password hashes to a DIFFERENT
//      value every time — the dump no longer reveals who shares a password, and rainbow tables are useless.
//   2) It's deliberately SLOW, tuned by a "work factor" (cost). Slow-for-you (a few ms per login) is
//      catastrophic-for-an-attacker (billions of guesses become years).
// The salt and cost are stored inside the hash string, so verification needs nothing but the hash.
public class BcryptSafe {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();   // default work factor (cost) = 10

        String hash1 = encoder.encode("correcthorse");
        String hash2 = encoder.encode("correcthorse");   // same password, encoded again

        System.out.println("=== BCRYPT — salted + slow ===");
        System.out.println("encode(\"correcthorse\") twice:");
        System.out.println("  " + hash1);
        System.out.println("  " + hash2);
        System.out.println("Same password -> DIFFERENT hashes (each has its own random salt). alice and carol");
        System.out.println("would now look unrelated in the DB. The format is $2a$<cost>$<salt+hash>.");

        System.out.println();
        System.out.println("Verification still works, because the salt is stored inside the hash:");
        System.out.println("  matches(\"correcthorse\", hash1) = " + encoder.matches("correcthorse", hash1));
        System.out.println("  matches(\"wrong\", hash1)        = " + encoder.matches("wrong", hash1));

        // Show the deliberate cost. Same number of hashes, BCrypt vs the fast hash.
        int n = 50;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            encoder.encode("correcthorse");
        }
        long bcryptMs = (System.nanoTime() - t0) / 1_000_000;

        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            sha256("correcthorse");
        }
        long shaMs = Math.max(1, (System.nanoTime() - t1) / 1_000_000);

        System.out.println();
        System.out.printf("Hashing %d passwords: BCrypt = %d ms, SHA-256 = %d ms (~%dx slower).%n",
                n, bcryptMs, shaMs, Math.max(1, bcryptMs / shaMs));
        System.out.println("That slowness is the whole point. The FastHashVulnerable dictionary attack that");
        System.out.println("finished in milliseconds would now take orders of magnitude longer per guess.");
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

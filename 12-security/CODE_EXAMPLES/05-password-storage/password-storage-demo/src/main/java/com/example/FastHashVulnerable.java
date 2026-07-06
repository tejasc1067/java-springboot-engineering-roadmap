package com.example;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

// "We don't store plaintext — we hash with SHA-256!" This feels safe and is still broken, for two reasons
// you can watch happen below:
//   1) No salt: identical passwords produce identical hashes, so the dump still reveals who shares a password
//      (and lets an attacker attack all of them at once, or use a precomputed rainbow table).
//   2) Too fast: SHA-256 is built to be fast, so a dictionary attack tries millions of guesses per second.
//      This class cracks the whole table from a small wordlist in milliseconds.
public class FastHashVulnerable {

    public static void main(String[] args) {
        Map<String, String> userTable = new LinkedHashMap<>();
        userTable.put("alice", sha256("correcthorse"));
        userTable.put("bob", sha256("hunter2"));
        userTable.put("carol", sha256("correcthorse"));

        System.out.println("=== UNSALTED FAST HASH (SHA-256) — looks safe, isn't ===");
        System.out.println("Your users table, as stored:");
        userTable.forEach((user, hash) -> System.out.println("  " + user + " -> " + hash));

        System.out.println();
        System.out.println("Problem 1 — no salt: alice and carol have IDENTICAL hashes, so the dump still");
        System.out.println("reveals they share a password. Hashing alone did not hide that.");

        // A tiny wordlist stands in for the millions-of-entries lists attackers actually use.
        String[] dictionary = {
                "123456", "password", "qwerty", "letmein", "hunter2", "correcthorse", "admin", "iloveyou"
        };

        System.out.println();
        System.out.println("Problem 2 — too fast: run a dictionary against the dump...");
        long start = System.nanoTime();
        for (Map.Entry<String, String> row : userTable.entrySet()) {
            for (String guess : dictionary) {
                if (sha256(guess).equals(row.getValue())) {
                    System.out.println("  cracked " + row.getKey() + " = \"" + guess + "\"");
                    break;
                }
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("Cracked the entire table in " + elapsedMs + " ms.");
        System.out.println("A real GPU does billions of SHA-256 guesses per second. Fast hashes are the wrong tool.");
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

package com.example;

import java.util.LinkedHashMap;
import java.util.Map;

// THE NAIVE WAY. Store the password exactly as the user typed it. Run this and look at the output: the
// "database" IS the list of everyone's passwords in plain view. A single leaked backup, a mis-scoped
// SELECT, a stray log line — and every account is compromised, on your site and everywhere the user
// reused that password. This class exists so you can see how total the failure is.
public class PlaintextStoreVulnerable {

    public static void main(String[] args) {
        Map<String, String> userTable = new LinkedHashMap<>();
        userTable.put("alice", "correcthorse");
        userTable.put("bob", "hunter2");
        userTable.put("carol", "correcthorse");   // carol happens to reuse alice's password

        System.out.println("=== PLAINTEXT storage — the naive way ===");
        System.out.println("Your users table, as actually stored:");
        userTable.forEach((user, pw) -> System.out.println("  " + user + " -> " + pw));

        System.out.println();
        System.out.println("An attacker who dumps this table is DONE — every password is immediately usable.");
        System.out.println("And because the values are identical, you can SEE that alice and carol share a");
        System.out.println("password, which likely works on their email, bank, and everything else they reused it on.");
    }
}

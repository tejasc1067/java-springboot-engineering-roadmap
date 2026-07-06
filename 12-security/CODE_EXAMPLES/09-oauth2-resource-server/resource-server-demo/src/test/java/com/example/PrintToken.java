package com.example;

import org.junit.jupiter.api.Test;

// Convenience for the manual curl walkthrough WITHOUT running a real IdP. Run:
//   mvn -q test -Dtest=PrintToken
// and copy a token from the output. This just calls the same TokenIssuer the tests use (it is playing the
// authorization server). With a real Keycloak you would instead fetch a token from its /token endpoint — see
// the demo README.
class PrintToken {

    @Test
    void printTokens() throws Exception {
        String readOnly = TokenIssuer.issue("alice", "books:read");
        String readWrite = TokenIssuer.issue("alice", "books:read books:write");
        System.out.println("\n=== BEARER TOKEN (scope: books:read) ===\n" + readOnly);
        System.out.println("\n=== BEARER TOKEN (scope: books:read books:write) ===\n" + readWrite + "\n");
    }
}

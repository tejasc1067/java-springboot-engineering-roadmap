package com.example.inventory;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;

/**
 * The base class the GENERATED contract tests extend. Its job is to put the provider into a
 * known state before each generated test fires a contract's request. Here that's just a
 * standalone MockMvc around the real controller — no server, no context, fast. In a richer
 * service this is where you'd stub the controller's own collaborators (its repository, etc.)
 * so the contract test exercises the web layer against known data.
 */
public abstract class ContractVerifierBase {

    @BeforeEach
    void setup() {
        RestAssuredMockMvc.standaloneSetup(new InventoryController());
    }
}

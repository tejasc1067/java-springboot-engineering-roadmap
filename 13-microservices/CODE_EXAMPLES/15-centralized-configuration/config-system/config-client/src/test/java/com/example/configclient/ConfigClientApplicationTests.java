package com.example.configclient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the client context. The config import is "optional:", so this passes even with
 * no config server running (the app falls back to the default @Value). To see it fetch
 * real config, run both apps per the README.
 */
@SpringBootTest
class ConfigClientApplicationTests {

    @Test
    void contextLoads() {
    }
}

package com.example.stateless;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The cart isn't the only state an instance can hold. Two more kinds, same bug, same fix. */
class LocalStateTest {

    @Test
    void fileSavedOnOneInstancesDisk_isNotFoundByTheOther(@TempDir Path diskA, @TempDir Path diskB) throws Exception {
        UploadService a = new UploadService(diskA);
        UploadService b = new UploadService(diskB);

        a.save("ann.png", new byte[] {1, 2, 3});

        assertThat(b.load("ann.png")).isEmpty();   // the avatar is broken on every other request
    }

    @Test
    void fileInSharedStorage_isFoundByEveryInstance(@TempDir Path shared) throws Exception {
        UploadService a = new UploadService(shared);
        UploadService b = new UploadService(shared);

        a.save("ann.png", new byte[] {1, 2, 3});

        assertThat(b.load("ann.png")).hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(1, 2, 3));
    }

    @Test
    void perInstanceCounter_multipliesTheLimitByTheInstanceCount() {
        List<LoginGuard> instances = List.of(new LoginGuard(new HashMap<>()), new LoginGuard(new HashMap<>()),
                new LoginGuard(new HashMap<>()));
        LoadBalancer<LoginGuard> balancer = LoadBalancer.roundRobin(instances);

        long guesses = IntStream.range(0, 100).filter(i -> balancer.pick("ann").tryWrongPassword("ann")).count();

        assertThat(guesses).isEqualTo(3L * LoginGuard.MAX_FAILURES);   // 9 password guesses, not 3
    }

    @Test
    void sharedCounter_enforcesTheLimitOnce() {
        var shared = new ConcurrentHashMap<String, Integer>();
        LoadBalancer<LoginGuard> balancer = LoadBalancer.roundRobin(
                List.of(new LoginGuard(shared), new LoginGuard(shared), new LoginGuard(shared)));

        long guesses = IntStream.range(0, 100).filter(i -> balancer.pick("ann").tryWrongPassword("ann")).count();

        assertThat(guesses).isEqualTo(LoginGuard.MAX_FAILURES);
    }
}

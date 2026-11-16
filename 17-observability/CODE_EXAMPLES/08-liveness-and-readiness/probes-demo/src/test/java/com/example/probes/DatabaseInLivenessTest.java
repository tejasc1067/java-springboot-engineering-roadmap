package com.example.probes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Broken: the database check is in the liveness group. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("broken-liveness")
class DatabaseInLivenessTest {

    @Autowired MockMvc mvc;
    @Autowired DatabaseHealthIndicator database;

    @AfterEach
    void databaseBack() {
        database.setReachable(true);
    }

    @Test
    void aDatabaseOutage_restartsThePodAgainAndAgain() {
        Kubelet kubelet = DatabaseOutage.simulate(mvc, database);

        // Every restart lands in the outage, so the fresh container fails liveness again: a restart loop.
        // Every pod talks to the same database, so every pod does this at the same time.
        assertThat(kubelet.restarts()).containsExactly(80, 121, 162, 203);
        assertThat(kubelet.wasReadyAt(300)).isTrue();
    }
}

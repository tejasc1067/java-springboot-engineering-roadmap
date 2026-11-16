package com.example.probes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Fixed: liveness checks only the app; the database is in readiness. */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseInReadinessTest {

    @Autowired MockMvc mvc;
    @Autowired DatabaseHealthIndicator database;

    @AfterEach
    void databaseBack() {
        database.setReachable(true);
    }

    @Test
    void aDatabaseOutage_takesThePodOutOfTheService_withoutRestartingIt() {
        Kubelet kubelet = DatabaseOutage.simulate(mvc, database);

        assertThat(kubelet.restarts()).isEmpty();
        assertThat(kubelet.wasReadyAt(DatabaseOutage.OUTAGE_START - 1)).isTrue();
        assertThat(kubelet.wasReadyAt(DatabaseOutage.OUTAGE_START + 30)).isFalse();   // out of the Service
        assertThat(kubelet.wasReadyAt(DatabaseOutage.OUTAGE_END)).isTrue();           // back the moment the database is
    }
}

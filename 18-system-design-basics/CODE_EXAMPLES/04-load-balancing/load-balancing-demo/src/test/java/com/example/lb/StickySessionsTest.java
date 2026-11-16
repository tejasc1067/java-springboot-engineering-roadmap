package com.example.lb;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StickySessionsTest {

    static void everyoneLogsIn(StickySessions lb, int users) {
        for (int u = 0; u < users; u++) lb.handle("user-" + u);
    }

    static void everyoneClicks(StickySessions lb, int users, int clicks) {
        for (int c = 0; c < clicks; c++) {
            for (int u = 0; u < users; u++) lb.handle("user-" + u);
        }
    }

    @Test
    void afterAScaleOut_theNewInstanceGetsNoneOfTheExistingUsers() {
        StickySessions lb = new StickySessions("a", "b");
        everyoneLogsIn(lb, 1_000);

        lb.addInstance("c");   // a and b are overloaded, so we add c
        lb.resetCounts();
        everyoneClicks(lb, 1_000, 10);

        System.out.println("after scale-out: " + lb.requestsPerInstance());
        assertThat(lb.requestsPerInstance()).containsEntry("a", 5_000).containsEntry("b", 5_000)
                .containsEntry("c", 0);
    }

    @Test
    void onlyNewUsersReachTheNewInstance() {
        StickySessions lb = new StickySessions("a", "b");
        everyoneLogsIn(lb, 1_000);
        lb.addInstance("c");
        lb.resetCounts();

        for (int u = 1_000; u < 1_300; u++) lb.handle("user-" + u);

        assertThat(lb.requestsPerInstance()).containsEntry("c", 100);   // a third of 300 new users
    }

    @Test
    void whenAnInstanceDies_everySessionOnItIsLost() {
        StickySessions lb = new StickySessions("a", "b", "c");
        everyoneLogsIn(lb, 900);

        lb.kill("a");
        everyoneClicks(lb, 900, 1);

        assertThat(lb.sessionsLost()).isEqualTo(300);   // a third of the users are logged out
    }
}

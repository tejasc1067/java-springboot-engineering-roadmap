package com.example.probes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.test.web.servlet.MockMvc;

import com.example.probes.Kubelet.Probe;
import com.example.probes.Kubelet.Response;

/**
 * One pod, ten simulated minutes, and a three-minute database outage from 01:00 to 04:00.
 * The probes call the real Spring Boot endpoints through MockMvc.
 */
final class DatabaseOutage {

    static final int OUTAGE_START = 60;
    static final int OUTAGE_END = 240;
    static final int APP_STARTUP_SECONDS = 20;   // until Tomcat answers after a (re)start
    static final Probe LIVENESS = new Probe(20, 10, 1, 3);
    static final Probe READINESS = new Probe(20, 10, 1, 3);

    static Kubelet simulate(MockMvc mvc, DatabaseHealthIndicator database) {
        return Kubelet.withoutStartupProbe(
                        LIVENESS, endpoint(mvc, database, "/actuator/health/liveness"),
                        READINESS, endpoint(mvc, database, "/actuator/health/readiness"))
                .run(600);
    }

    private static Kubelet.Endpoint endpoint(MockMvc mvc, DatabaseHealthIndicator database, String path) {
        return (now, uptime) -> {
            if (uptime < APP_STARTUP_SECONDS) return Response.refused();
            database.setReachable(now < OUTAGE_START || now >= OUTAGE_END);
            try {
                int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
                return status == 200 ? Response.ok() : Response.failed();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private DatabaseOutage() { }
}

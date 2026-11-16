package com.example.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * BROKEN: Spring Session switched off, so each instance keeps sessions in its own Tomcat's memory
 * (the default for any Spring Boot app without Spring Session).
 */
class InMemorySessionTest {

    private static final String SESSIONS_IN_TOMCAT_MEMORY =
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration";

    @Test
    void loginOnA_isUnknownToB_soTheUserIsLoggedOutOnTheirNextRequest() {
        try (TwoInstances app = new TwoInstances(SESSIONS_IN_TOMCAT_MEMORY)) {
            app.post(app.a, "/login?user=ann");

            assertThat(app.get(app.a, "/me").body()).isEqualTo("ann on A");
            // The load balancer sends the next request to B. Same JSESSIONID cookie, but B has never seen that id.
            var onB = app.get(app.b, "/me");
            assertThat(onB.statusCode()).isEqualTo(401);
            assertThat(onB.body()).isEqualTo("not logged in on B");
            assertThat(app.cookies.getCookieStore().getCookies()).extracting("name").containsExactly("JSESSIONID");
        }
    }

    @Test
    void nonSerializableAttribute_worksFineInMemory_soTheBugHidesUntilSessionsMoveToRedis() {
        try (TwoInstances app = new TwoInstances(SESSIONS_IN_TOMCAT_MEMORY)) {
            app.post(app.a, "/login?user=ann");

            assertThat(app.post(app.a, "/cart/broken?item=book").statusCode()).isEqualTo(200);
        }
    }
}

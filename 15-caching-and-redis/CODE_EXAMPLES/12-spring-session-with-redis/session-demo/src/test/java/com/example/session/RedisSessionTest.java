package com.example.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/**
 * FIXED: spring-session-data-redis on the classpath. Both instances read and write sessions in one Redis.
 * The apps are started by hand (two of them), so the Redis address is passed as properties
 * rather than with @ServiceConnection, which only works for a @SpringBootTest's own context.
 */
class RedisSessionTest {

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    static TwoInstances app;

    @BeforeAll
    static void start() {
        REDIS.start();
        app = new TwoInstances("spring.data.redis.host=" + REDIS.getHost(),
                "spring.data.redis.port=" + REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void stop() {
        app.close();
        REDIS.stop();
    }

    @Test
    void loginOnA_isVisibleOnB_becauseTheSessionIsInRedis() {
        app.post(app.a, "/login?user=ann");

        assertThat(app.get(app.b, "/me").body()).isEqualTo("ann on B");
        assertThat(app.cookies.getCookieStore().getCookies()).extracting("name").containsExactly("SESSION");
    }

    @Test
    void logoutOnB_logsTheUserOutOnA_too() {
        app.post(app.a, "/login?user=ann");

        app.post(app.b, "/logout");

        assertThat(app.get(app.a, "/me").statusCode()).isEqualTo(401);
    }

    @Test
    void theSessionIsARedisHash_withTheAttributesAndATtlFromTheSessionTimeout() {
        app.post(app.a, "/login?user=ann");
        String key = "spring:session:sessions:" + sessionId();
        StringRedisTemplate redis = app.a.getBean(StringRedisTemplate.class);

        assertThat(redis.opsForHash().keys(key)).extracting(Object::toString)
                .contains("creationTime", "lastAccessedTime", "maxInactiveInterval", "sessionAttr:user");
        // server.servlet.session.timeout: 15m. Every request that touches the session resets the TTL.
        assertThat(redis.getExpire(key)).isBetween(890L, 900L);
    }

    @Test
    void nonSerializableAttribute_failsTheRequest_onceSessionsAreStoredInRedis() {
        app.post(app.a, "/login?user=ann");

        assertThat(app.post(app.a, "/cart/broken?item=book").statusCode()).isEqualTo(500);
        assertThat(app.post(app.a, "/cart/fixed?item=book").statusCode()).isEqualTo(200);
    }

    /** The SESSION cookie holds the session id, Base64-encoded. */
    private static String sessionId() {
        HttpCookie cookie = app.cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("SESSION")).findFirst().orElseThrow();
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }
}

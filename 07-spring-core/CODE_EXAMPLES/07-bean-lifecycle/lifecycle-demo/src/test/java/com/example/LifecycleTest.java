package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleTest {

    @Test
    void cacheIsWarmedBeforeFirstUse() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            WarmedCache cache = ctx.getBean(WarmedCache.class);

            // We did nothing to fill it. @PostConstruct already ran.
            assertThat(cache.size()).isEqualTo(2);
            assertThat(cache.get("ABC")).isEqualTo("alphabet");
        }
    }

    @Test
    void resourceIsOpenWhileTheContextIsRunning() {
        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            LifecycleResource resource = ctx.getBean(LifecycleResource.class);

            assertThat(resource.isOpen()).isTrue();
        }
    }

    @Test
    void resourceClosesWhenTheContextCloses() {
        LifecycleResource resource;

        try (var ctx = new AnnotationConfigApplicationContext(AppConfig.class)) {
            resource = ctx.getBean(LifecycleResource.class);
            assertThat(resource.isOpen()).isTrue();
        }
        // try-with-resources closed the context. @PreDestroy on LifecycleResource ran.
        // The Java reference still points to the object so we can inspect its state.
        assertThat(resource.isOpen()).isFalse();
    }
}

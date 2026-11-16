package com.example.springcache;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class NoEnableCachingTest {

    // The same beans and the same cache auto-configuration as the app, with and without CacheConfig.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
            .withBean(ProductRepository.class)
            .withBean(ProductService.class);

    @Test
    void withoutEnableCaching_cacheableIsSilentlyIgnored() {
        runner.run(context -> {
            ProductRepository repository = context.getBean(ProductRepository.class);
            repository.save(new Product(1, "Kettle", 2500));
            ProductService service = context.getBean(ProductService.class);

            service.findById(1);
            service.findById(1);

            assertThat(repository.reads()).isEqualTo(2);              // no error, no caching
            assertThat(context).doesNotHaveBean(CacheManager.class);
        });
    }

    @Test
    void withEnableCaching_theSameCodeCaches() {
        runner.withUserConfiguration(CacheConfig.class).run(context -> {
            ProductRepository repository = context.getBean(ProductRepository.class);
            repository.save(new Product(1, "Kettle", 2500));
            ProductService service = context.getBean(ProductService.class);

            service.findById(1);
            service.findById(1);

            assertThat(repository.reads()).isEqualTo(1);
        });
    }
}

package com.example.springcache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

// Without this, every @Cacheable in the app is silently ignored (NoEnableCachingTest).
@Configuration
@EnableCaching
public class CacheConfig {
}

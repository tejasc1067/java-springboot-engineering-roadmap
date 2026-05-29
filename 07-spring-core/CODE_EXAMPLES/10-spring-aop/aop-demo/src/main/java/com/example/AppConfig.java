package com.example;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

// @EnableAspectJAutoProxy tells Spring to look for @Aspect beans and wrap matching
// targets in proxies. Without it, the @Aspect class is just a bean that never fires.
@Configuration
@ComponentScan(basePackages = "com.example")
@EnableAspectJAutoProxy
public class AppConfig {
}

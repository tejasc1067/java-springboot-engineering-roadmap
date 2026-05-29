package com.example;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

// Compare this to topic 02's AppConfig: five @Bean methods are gone. The scanner
// will find every @Service / @Component / @Repository / @Controller in com.example
// (and its subpackages, but we have none) and register them automatically.
@Configuration
@ComponentScan(basePackages = "com.example")
public class AppConfig {
}

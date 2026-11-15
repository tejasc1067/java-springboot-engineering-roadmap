package com.example.synccall;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** Feign clients only exist inside a Spring context with @EnableFeignClients. */
@SpringBootApplication
@EnableFeignClients
public class FeignDemoApp {
}

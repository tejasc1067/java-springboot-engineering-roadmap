package com.example.cdn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@SpringBootApplication
public class CdnDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CdnDemoApplication.class, args);
    }

    /** Adds an ETag computed from the response body, and answers 304 when it matches If-None-Match. */
    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtag() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/reports/*");
        return registration;
    }
}

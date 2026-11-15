package com.example;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ServletConfig {

    // Registered beside the DispatcherServlet (which Boot maps to "/").
    // Tomcat picks the most specific mapping, so /raw/* never reaches Spring MVC.
    @Bean
    ServletRegistrationBean<RawBookServlet> rawBookServlet() {
        return new ServletRegistrationBean<>(new RawBookServlet(), "/raw/books/*");
    }
}

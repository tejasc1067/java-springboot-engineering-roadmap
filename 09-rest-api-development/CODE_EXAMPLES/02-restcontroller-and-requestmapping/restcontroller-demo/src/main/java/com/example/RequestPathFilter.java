package com.example;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Filters run before any servlet, so this header shows up on both the raw
// servlet's responses and the DispatcherServlet's. Spring Security (module 12)
// is a chain of filters sitting in exactly this spot.
@Component
public class RequestPathFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        resp.setHeader("X-Passed-Filter", "true");
        chain.doFilter(req, resp);
    }
}

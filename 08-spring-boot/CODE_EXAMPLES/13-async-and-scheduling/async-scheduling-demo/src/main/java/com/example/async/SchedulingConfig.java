package com.example.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

// Each job class below is behind its own profile, so every test turns on only the jobs it is about.
@EnableScheduling
@Configuration
@Profile({"starvation", "rate-vs-delay", "digest-unguarded", "digest-claimed"})
public class SchedulingConfig {
}

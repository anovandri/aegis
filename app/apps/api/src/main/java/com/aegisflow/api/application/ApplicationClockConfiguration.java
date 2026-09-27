package com.aegisflow.api.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationClockConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

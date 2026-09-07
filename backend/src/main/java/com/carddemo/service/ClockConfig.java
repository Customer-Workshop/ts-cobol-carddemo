package com.carddemo.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** B-0030: FUNCTION CURRENT-DATE becomes an injectable {@link Clock} so tests can pin time. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}

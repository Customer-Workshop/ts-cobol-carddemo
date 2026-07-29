package com.carddemo.cbact04c.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Cbact04cProperties.class)
public class Cbact04cConfiguration {

    @Bean
    public Clock cbact04cClock() {
        return Clock.systemDefaultZone();
    }
}

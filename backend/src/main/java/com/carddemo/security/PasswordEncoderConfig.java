package com.carddemo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt by default ({bcrypt} prefixed); legacy USRSEC plaintext is upgraded on login (B-0026, D-0029).
 * Not web-conditional: the headless {@code import} profile builds the same service graph.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}

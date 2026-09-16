package com.carddemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CardDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CardDemoApplication.class, args);
    }

    // S11-B6: the CICS ASKTIME/FORMATTIME clock becomes an injectable bean
    // so services can be pinned to a fixed instant in tests.
    @org.springframework.context.annotation.Bean
    java.time.Clock carddemoClock() {
        return java.time.Clock.systemDefaultZone();
    }
}

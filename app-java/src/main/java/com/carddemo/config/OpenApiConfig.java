package com.carddemo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cardDemoOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CardDemo API")
                        .description("CardDemo Account Subsystem — migrated from COBOL/CICS/VSAM")
                        .version("1.0.0"));
    }
}

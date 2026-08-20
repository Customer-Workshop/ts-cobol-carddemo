package com.carddemo.security;

import com.carddemo.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.Instant;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder usrsecPasswordEncoder() {
        return new UsrsecPlaintextPasswordEncoder();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, ObjectMapper objectMapper, SecurityContextRepository repository) throws Exception {
        http
                // CSRF is disabled because this is a session-backed JSON API.
                .csrf(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(repository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/signon").permitAll()
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(jsonEntryPoint(objectMapper))
                        .accessDeniedHandler(jsonDeniedHandler(objectMapper)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private AuthenticationEntryPoint jsonEntryPoint(ObjectMapper mapper) {
        return (request, response, exception) ->
                writeError(mapper, response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
    }

    private AccessDeniedHandler jsonDeniedHandler(ObjectMapper mapper) {
        return (request, response, exception) ->
                writeError(mapper, response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
    }

    private void writeError(ObjectMapper mapper, HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), new ErrorResponse(message, status, Instant.now()));
    }
}

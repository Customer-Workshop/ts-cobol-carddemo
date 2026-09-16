package com.carddemo.security;

import com.carddemo.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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
                        .requestMatchers("/", "/css/**", "/signon").permitAll()
                        .requestMatchers("/api/auth/signon").permitAll()
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(entryPoint(objectMapper))
                        .accessDeniedHandler(deniedHandler(objectMapper)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    // The UI and JSON surfaces share the session: unsigned UI navigation is
    // sent to the sign-on screen (the 3270 re-sign-on flow), while API and
    // console paths keep their JSON error contract.
    private AuthenticationEntryPoint entryPoint(ObjectMapper mapper) {
        return (request, response, exception) -> {
            if (isApiSurface(request)) {
                writeError(mapper, response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            } else {
                response.sendRedirect(request.getContextPath() + "/signon");
            }
        };
    }

    private AccessDeniedHandler deniedHandler(ObjectMapper mapper) {
        return (request, response, exception) -> {
            if (isApiSurface(request)) {
                writeError(mapper, response, HttpServletResponse.SC_FORBIDDEN, "Access denied");
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            }
        };
    }

    private boolean isApiSurface(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith(request.getContextPath() + "/api/")
                || path.startsWith(request.getContextPath() + "/h2-console/");
    }

    private void writeError(ObjectMapper mapper, HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), new ErrorResponse(message, status, Instant.now()));
    }
}

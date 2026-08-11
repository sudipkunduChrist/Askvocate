package com.askvocate.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for the Askvocate backend.
 * 
 * <p>Configures JWT-based stateless authentication via OAuth2 Resource Server.
 * All {@code /api/documents/**} endpoints require an authenticated JWT bearer token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless API — no CSRF needed
            .csrf(csrf -> csrf.disable())

            // Session management — never create HTTP sessions
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Health/actuator endpoints remain open
                .requestMatchers("/actuator/**").permitAll()
                // All document verification endpoints require authentication
                .requestMatchers("/api/documents/**").authenticated()
                // Everything else requires authentication by default
                .anyRequest().authenticated()
            )

            // JWT-based OAuth2 Resource Server
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> {})
            );

        return http.build();
    }
}

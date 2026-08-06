package com.askvocate.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF so POST requests don't get rejected
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/verification/**").permitAll() // Permit all verification requests
                .anyRequest().permitAll() // Allow other endpoints to be tested without auth
            );
        return http.build();
    }
}

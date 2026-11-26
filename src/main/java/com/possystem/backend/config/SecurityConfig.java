package com.possystem.backend.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    // Truy cập không cần security
    private static final String[] PUBLIC_POST_ENDPOINTS = {
            "/auth/token", "/auth/introspect", "/auth/logout", "/auth/refresh",
            "/payments/webhook/**"
    };

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/actuator/health", "/actuator/info", "/actuator/prometheus",
            "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html",
            "/ws/pos/**"
    };

    private final CustomJwtDecoder customJwtDecoder;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final Environment env;
    private final ObjectProvider<RateLimitingFilter> rateLimitingFilterProvider;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOrigins;

    public SecurityConfig(CustomJwtDecoder customJwtDecoder,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            Environment env,
            ObjectProvider<RateLimitingFilter> rateLimitingFilterProvider) {
        this.customJwtDecoder = customJwtDecoder;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.env = env;
        this.rateLimitingFilterProvider = rateLimitingFilterProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration corsConfig = new CorsConfiguration();
                    if (allowedOrigins != null && !allowedOrigins.isBlank()) {
                        for (String origin : allowedOrigins.split(",")) {
                            String trimmed = origin.trim();
                            if (!trimmed.isEmpty()) {
                                corsConfig.addAllowedOrigin(trimmed);
                            }
                        }
                    } else {
                        corsConfig.addAllowedOrigin("http://localhost:5173");
                    }
                    corsConfig.addAllowedMethod("*");
                    corsConfig.addAllowedHeader("*");
                    corsConfig.setAllowCredentials(true);
                    corsConfig.addExposedHeader("Authorization");
                    corsConfig.addExposedHeader("X-Total-Count");
                    corsConfig.addExposedHeader("X-Trace-Id");
                    return corsConfig;
                }))
                .authorizeHttpRequests(request -> {
                    // Cho phép public endpoints
                    request.requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll();
                    request.requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll();

                    // Các API khác cần login
                    request.anyRequest().authenticated();
                });

        httpSecurity.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwtConfigurer -> jwtConfigurer
                .decoder(customJwtDecoder)
                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(jwtAuthenticationEntryPoint));

        httpSecurity.headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.deny())
                .contentTypeOptions(contentTypeOptions -> {})
        );

        httpSecurity.csrf(AbstractHttpConfigurer::disable);

        // Execute rate limiter after BearerTokenAuthenticationFilter so authenticated User ID is populated
        RateLimitingFilter rateLimitingFilter = rateLimitingFilterProvider.getIfAvailable();
        if (rateLimitingFilter != null) {
            httpSecurity.addFilterAfter(rateLimitingFilter, BearerTokenAuthenticationFilter.class);
        }

        return httpSecurity.build();
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(RateLimitingFilter filter) {
        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
        // Managed inside Spring Security FilterChain to avoid duplicate execution per request
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}

package ru.example.prodbackend.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration(proxyBeanMethods = false)
public class MetricsSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain metricsSecurity(HttpSecurity http,
            @Value("${observability.metrics-token:}") String token) throws Exception {
        byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        return http.securityMatcher("/actuator/prometheus", "/actuator/prometheus/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().access((authentication, context) -> {
                    var request = context.getRequest();
                    String supplied = request.getHeader("Authorization");
                    return new AuthorizationDecision(!token.isBlank()
                            && HttpMethod.GET.matches(request.getMethod())
                            && supplied != null
                            && MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8)));
                }))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> {
                            response.setHeader("WWW-Authenticate", "Bearer");
                            response.setStatus(401);
                        })
                        .accessDeniedHandler((request, response, error) -> response.setStatus(403)))
                .build();
    }
}

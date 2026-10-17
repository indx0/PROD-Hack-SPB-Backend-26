package ru.example.prodbackend.auth.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonMapper jsonMapper;

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter>
    jwtFilterRegistration(JwtAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(req -> req
                        //docs
                        .requestMatchers(
                                "/docs",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-resources/**",
                                "/js/openapi-to-markdown.js",
                                "/js/swagger-autumn.js",
                                "/css/swagger-autumn.css"
                        ).permitAll()

                        //auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/authenticate",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/logout-all"
                        ).permitAll()

                        //pings
                        .requestMatchers(HttpMethod.GET,
                                "/api/demo/ping",
                                "/actuator/health",
                                "/actuator/health/readiness",
                                "/actuator/health/liveness"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/demo/authping")
                        .authenticated()

                        //user
                        .requestMatchers(HttpMethod.GET, "/api/user/me")
                        .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/user/me")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/user/change-password")
                        .authenticated()

                        .anyRequest().authenticated()
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "Authentication required or access token is invalid"
                                )
                        )
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        "Access denied"
                                )
                        )
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String message
    ) throws IOException {
        ErrorResponse body = ErrorResponse.builder()
                .code(status.name())
                .message(message)
                .traceId(UUID.randomUUID())
                .time(Instant.now())
                .build();

        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}

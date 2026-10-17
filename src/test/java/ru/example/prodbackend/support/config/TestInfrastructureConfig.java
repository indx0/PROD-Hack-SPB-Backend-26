package ru.example.prodbackend.support.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.example.prodbackend.support.DatabaseCleaner;

@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructureConfig {
    @Bean
    @Primary
    PasswordEncoder testPasswordEncoder() {
        // Настоящий BCrypt с меньшей стоимостью только в тестовом контексте.
        return new BCryptPasswordEncoder(4);
    }

    @Bean
    DatabaseCleaner databaseCleaner(JdbcTemplate jdbc) { return new DatabaseCleaner(jdbc); }
}

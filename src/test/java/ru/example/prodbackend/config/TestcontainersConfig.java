package ru.example.prodbackend.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("prod_backend_test")
                .withUsername("test")
                .withPassword("test");
    }
}

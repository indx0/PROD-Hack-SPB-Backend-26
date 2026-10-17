package ru.example.prodbackend.integration.system;

import ru.example.prodbackend.support.AuthIntegrationTestSupport;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {"observability.metrics-token=test-scrape-secret",
        "management.metrics.tags.environment=prod"})
class ObservabilityIntegrationTest extends AuthIntegrationTestSupport {
    @Test
    void actualPrometheusEndpointRequiresIndependentToken() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"));
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
        var tokens = registerUser();
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + tokens.getAccessToken()))
                .andExpect(status().isUnauthorized());
        var body = mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer test-scrape-secret"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/observability"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/observability/scrape.prom"), body);
        assertThat(body).contains("jvm_memory_used_bytes", "movermate_llm_active",
                "movermate_llm_operation_seconds_count", "http_server_requests_seconds_count")
                .doesNotContain("test-scrape-secret", tokens.getAccessToken());
        mockMvc.perform(post("/actuator/prometheus").header("Authorization", "Bearer test-scrape-secret"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/demo/authping").header("Authorization", "Bearer test-scrape-secret"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        assertAccessTokenWorks(tokens.getAccessToken());
    }
}

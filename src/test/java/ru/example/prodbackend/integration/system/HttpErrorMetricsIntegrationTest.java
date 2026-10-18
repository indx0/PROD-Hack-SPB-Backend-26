package ru.example.prodbackend.integration.system;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import ru.example.prodbackend.observability.ErrorObservabilityAdvice;
import ru.example.prodbackend.support.AuthIntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"observability.metrics-token=test-scrape-secret",
        "management.metrics.tags.environment=prod"})
class HttpErrorMetricsIntegrationTest extends AuthIntegrationTestSupport {
    @Autowired
    private MeterRegistry meters;

    @Test
    void securityAndMultipartErrorsExposeActualResponseCodesInPrometheus() throws Exception {
        var unauthorized = meters.counter("movermate.http.errors", "method", "GET", "route", "UNMATCHED",
                "status", "401", "code", "UNAUTHORIZED");
        double previousUnauthorized = unauthorized.count();
        var securityResult = mockMvc.perform(get("/api/demo/authping"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(unauthorized.count()).isEqualTo(previousUnauthorized + 1);
        var securityBody = jsonMapper.readTree(securityResult.getResponse().getContentAsString());
        assertThat(securityResult.getRequest().getAttribute(ErrorObservabilityAdvice.ERROR_CODE))
                .isEqualTo(securityBody.path("code").asText());
        assertThat(securityResult.getRequest().getAttribute(ErrorObservabilityAdvice.ERROR_TRACE_ID).toString())
                .isEqualTo(securityBody.path("traceId").asText());

        var tokens = registerUser();
        var invalidMultipart = meters.counter("movermate.http.errors", "method", "POST",
                "route", "/api/ml/item-description", "status", "400", "code", "INVALID_MULTIPART");
        double previousMultipart = invalidMultipart.count();
        var multipartResult = mockMvc.perform(multipart("/api/ml/item-description")
                        .header("Authorization", "Bearer " + tokens.getAccessToken()))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(jsonMapper.readTree(multipartResult.getResponse().getContentAsString()).path("code").asText())
                .isEqualTo("INVALID_MULTIPART");
        assertThat(invalidMultipart.count()).isEqualTo(previousMultipart + 1);

        String scrape = mockMvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer test-scrape-secret"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(scrape.lines().filter(line -> line.startsWith("movermate_http_errors_total{"))
                .filter(line -> line.contains("code=\"INVALID_MULTIPART\"")))
                .anySatisfy(line -> assertThat(line).contains("application=\"movermate\"", "environment=\"prod\"",
                        "method=\"POST\"", "route=\"/api/ml/item-description\"", "status=\"400\""));
        assertThat(scrape).doesNotContain(tokens.getAccessToken(), "test-scrape-secret");
    }
}

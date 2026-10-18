package ru.example.prodbackend.unit.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import ru.example.prodbackend.exception.GlobalExceptionHandler;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.observability.ErrorObservabilityAdvice;
import ru.example.prodbackend.observability.RequestLoggingFilter;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HttpErrorObservationTest {
    @ParameterizedTest
    @CsvSource({"400,INVALID_MULTIPART", "422,VALIDATION_ERROR", "429,ML_RATE_LIMIT_EXCEEDED",
            "502,LLM_INVALID_RESPONSE", "503,LLM_UNAVAILABLE", "504,LLM_TIMEOUT"})
    void observesSerializedErrorsOnceWithoutPerRequestMetricLabels(int status, String code) throws Exception {
        var meters = new SimpleMeterRegistry();
        try {
            var filter = new RequestLoggingFilter(meters);
            var mvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                    .setControllerAdvice(new GlobalExceptionHandler(), new ErrorObservabilityAdvice())
                    .addFilters(filter).build();
            Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
            var appender = new ListAppender<ILoggingEvent>() {
                @Override protected void append(ILoggingEvent event) {
                    event.prepareForDeferredProcessing();
                    super.append(event);
                }
            };
            appender.start();
            logger.addAppender(appender);
            try {
                for (int i = 0; i < 2; i++) {
                    var result = mvc.perform(get("/errors/" + UUID.randomUUID())
                                    .param("status", Integer.toString(status)).param("code", code)
                                    .header("Authorization", "Bearer secret-token"))
                            .andExpect(status().is(status)).andReturn();
                    var body = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
                    assertThat(body.path("code").asText()).isEqualTo(code);
                    var event = appender.list.get(i);
                    assertThat(event.getKeyValuePairs()).anySatisfy(pair -> {
                        assertThat(pair.key).isEqualTo("traceId");
                        assertThat(pair.value).isEqualTo(body.path("traceId").asText());
                    });
                    assertThat(event.getMDCPropertyMap().get("requestId"))
                            .isEqualTo(result.getResponse().getHeader("X-Request-ID"));
                    assertThat(event.getKeyValuePairs().toString()).doesNotContain("secret-token", "safe-message");
                }
                assertThat(appender.list).hasSize(2);
                var counter = meters.get("movermate.http.errors").tags("method", "GET",
                        "route", "/errors/{id}", "status", Integer.toString(status), "code", code).counter();
                assertThat(counter.count()).isEqualTo(2);
                assertThat(meters.getMeters()).hasSize(1);
                assertThat(counter.getId().getTags()).hasSize(4);
            } finally {
                logger.detachAppender(appender);
                appender.stop();
            }
        } finally {
            meters.close();
        }
    }

    @Test
    void leavesSuccessfulResponsesUnchangedAndDoesNotCountThem() throws Exception {
        var meters = new SimpleMeterRegistry();
        try {
            var mvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                    .setControllerAdvice(new ErrorObservabilityAdvice())
                    .addFilters(new RequestLoggingFilter(meters)).build();
            var result = mvc.perform(get("/success")).andExpect(status().isOk()).andReturn();
            assertThat(result.getResponse().getContentAsString()).isEqualTo("ok");
            assertThat(meters.getMeters()).isEmpty();
        } finally {
            meters.close();
        }
    }

    @RestController
    static class ErrorController {
        @GetMapping("/errors/{id}")
        Object error(@PathVariable UUID id, @RequestParam int status,
                @RequestParam String code) {
            throw new MlException(HttpStatus.valueOf(status), code, "safe-message");
        }

        @GetMapping("/success")
        String success() {
            return "ok";
        }
    }
}

package ru.example.prodbackend.unit.observability;

import ru.example.prodbackend.observability.RequestLoggingFilter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RequestLoggingFilterTest {
    @Test
    void producesSafeJsonForSuccessAndSecurityFailuresAndRestoresContext() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        var context = new LoggerContext();
        context.putObject(Environment.class.getName(), new MockEnvironment());
        var encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("logstash");
        encoder.start();
        var ids = new ArrayList<String>();
        try {
            for (int status : List.of(200, 401, 403)) {
                var request = new MockHttpServletRequest("POST", "/api/items/private-user-id");
                request.setContent("private-photo-body".getBytes(StandardCharsets.UTF_8));
                request.addHeader("Authorization", "Bearer private-secret");
                request.addHeader("X-Request-ID", "untrusted-id");
                var response = new MockHttpServletResponse();
                new RequestLoggingFilter().doFilter(request, response, (req, res) -> {
                    req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/items/{id}");
                    ((jakarta.servlet.http.HttpServletResponse) res).setStatus(status);
                    assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-ID"));
                });
                ids.add(response.getHeader("X-Request-ID"));
                assertThat(MDC.get("requestId")).isNull();
            }
            assertThat(ids).doesNotHaveDuplicates().allSatisfy(id -> assertThat(id).hasSize(36));
            assertThat(appender.list).hasSize(3);
            for (int i = 0; i < 3; i++) {
                String json = new String(encoder.encode(appender.list.get(i)), StandardCharsets.UTF_8);
                var node = JsonMapper.builder().build().readTree(json);
                assertThat(node.path("requestId").asText()).isEqualTo(ids.get(i));
                assertThat(node.path("status").asInt()).isEqualTo(List.of(200, 401, 403).get(i));
                assertThat(node.path("route").asText()).isEqualTo("/api/items/{id}");
                assertThat(node.path("durationMs").asLong()).isGreaterThanOrEqualTo(0);
                assertThat(json).doesNotContain("private-secret", "private-photo-body", "private-user-id", "untrusted-id");
            }
            MDC.put("requestId", "outer");
            assertThatThrownBy(() -> new RequestLoggingFilter().doFilter(new MockHttpServletRequest(),
                    new MockHttpServletResponse(), (req, res) -> { throw new IllegalStateException("hidden"); }))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(MDC.get("requestId")).isEqualTo("outer");
            var failed = JsonMapper.builder().build().readTree(encoder.encode(appender.list.get(3)));
            assertThat(failed.path("status").asInt()).isEqualTo(500);
            assertThat(failed.path("route").asText()).isEqualTo("UNMATCHED");
        } finally {
            MDC.clear();
            logger.detachAppender(appender);
            appender.stop();
            encoder.stop();
            context.stop();
        }
    }
}

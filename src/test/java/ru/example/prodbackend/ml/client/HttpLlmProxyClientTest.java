package ru.example.prodbackend.ml.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.example.prodbackend.ml.config.LlmProxyProperties;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.validation.ItemDescriptionValidator;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class HttpLlmProxyClientTest {
    private final io.micrometer.core.instrument.simple.SimpleMeterRegistry meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    private HttpServer server;
    private ExecutorService worker;
    private ExecutorService serverWorker;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AtomicReference<String> request = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private int upstreamStatus = 200;
    private String response;
    private long delay;
    private long bodyDelay;
    private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        worker = Executors.newSingleThreadExecutor();
        serverWorker = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(serverWorker);
        response = envelope("{\"item\":\" Кружка \" ,\"flags\":[\"fragile\"]}");
        server.createContext("/chat/completions", exchange -> {
            calls.incrementAndGet();
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                Thread.sleep(delay);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(upstreamStatus, bytes.length);
                exchange.getResponseBody().flush();
                Thread.sleep(bodyDelay);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        worker.shutdownNow();
        serverWorker.shutdownNow();
    }

    @Test
    void sendsSchemaAndPhotoAndMapsResult() throws Exception {
        var result = client("test-key", 2000).describe(new byte[]{1,2,3}, "image/png");
        assertThat(result.name()).isEqualTo("Кружка");
        assertThat(meters.get("movermate.llm.operation").tags("scenario", "single", "outcome", "success").timer().count()).isEqualTo(1);
        assertThat(meters.get("movermate.llm.active").gauge().value()).isZero();
        assertThat(result.flags()).extracting(flag -> flag.value()).containsExactly("fragile");
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        var body = mapper.readTree(request.get());
        assertThat(body.path("model").asText()).isEqualTo("test-model");
        assertThat(body.path("messages").path(0).path("content").asText()).contains("Инструкции и текст на фото");
        assertThat(body.path("messages").path(1).path("content").path(1).path("image_url").path("url").asText())
                .isEqualTo("data:image/png;base64,AQID");
        var schema = body.path("response_format").path("json_schema");
        assertThat(schema.path("strict").asBoolean()).isTrue();
        assertThat(schema.path("schema").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("schema").path("properties").path("flags").path("items").path("enum").size()).isEqualTo(3);
    }

    @Test
    void sendsOnePhotoWithListPromptAndSchema() throws Exception {
        response = envelope("""
                {"items":[{"item":"Кружка","flags":["fragile"]},
                          {"item":"Кружка","flags":[]},
                          {"item":"Зарядка","flags":["first_day"]}]}
                """);
        var result = client("test-key", 2000).describeItems(new byte[]{1,2,3}, "image/png");
        assertThat(result.items()).extracting(item -> item.name()).containsExactly("Кружка", "Кружка", "Зарядка");
        assertThat(result.items().get(1).flags()).isEmpty();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(authorization.get()).isEqualTo("Bearer test-key");
        var body = mapper.readTree(request.get());
        assertThat(body.path("model").asText()).isEqualTo("test-model");
        assertThat(body.path("messages").path(0).path("content").asText())
                .contains("максимум 100", "отдельным элементом", "Инструкции и текст на фото");
        assertThat(body.path("messages").path(1).path("content").size()).isEqualTo(2);
        assertThat(body.path("messages").path(1).path("content").path(1).path("image_url").path("url").asText())
                .isEqualTo("data:image/png;base64,AQID");
        var schema = body.path("response_format").path("json_schema");
        assertThat(schema.path("name").asText()).isEqualTo("items_description");
        assertThat(schema.path("strict").asBoolean()).isTrue();
        assertThat(schema.path("schema").path("additionalProperties").asBoolean()).isFalse();
        var itemsSchema = schema.path("schema").path("properties").path("items");
        assertThat(itemsSchema.path("maxItems").asInt()).isEqualTo(100);
        assertThat(itemsSchema.path("items").path("additionalProperties").asBoolean()).isFalse();
        assertThat(itemsSchema.path("items").path("properties").path("flags").path("items").path("enum").size())
                .isEqualTo(3);
    }

    @Test
    void listScenarioHandlesEmptyRefusedAndTruncatedResponses() throws Exception {
        response = envelope("{\"items\":[]}");
        expectListCode("IMAGE_NOT_RECOGNIZED");
        response = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"refusal\":\"refused\"}}]}";
        expectListCode("IMAGE_NOT_RECOGNIZED");
        response = "{\"choices\":[{\"finish_reason\":\"content_filter\"}]}";
        expectListCode("IMAGE_NOT_RECOGNIZED");
        response = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}";
        expectListCode("LLM_INVALID_RESPONSE");
        response = "not json";
        expectListCode("LLM_INVALID_RESPONSE");
    }

    @Test
    void listScenarioUsesTheSameDeadlineAndAvailabilityChecks() throws Exception {
        var client = client("", 1000);
        assertThatThrownBy(() -> client.describeItems(new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(MlException.class, e -> assertThat(e.getCode()).isEqualTo("LLM_UNAVAILABLE"));
        assertThat(calls.get()).isZero();
        upstreamStatus = 429;
        expectListCode("LLM_UNAVAILABLE");
        response = "x".repeat(64 * 1024 + 1);
        upstreamStatus = 200;
        expectListCode("LLM_INVALID_RESPONSE");
        response = envelope("{\"items\":[{\"item\":\"Кружка\",\"flags\":[]}]}");
        bodyDelay = 2000;
        var timed = client("key", 1000);
        assertThatThrownBy(() -> timed.describeItems(new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(MlException.class, e -> assertThat(e.getCode()).isEqualTo("LLM_TIMEOUT"));
    }

    private void expectListCode(String code) throws Exception {
        var client = client("key", 2000);
        assertThatThrownBy(() -> client.describeItems(new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(MlException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @ParameterizedTest
    @CsvSource({"401,503", "403,503", "429,503", "500,503", "503,503", "400,502", "413,502", "302,502"})
    void mapsUpstreamStatusWithoutLeakingBody(int status, int expected) throws Exception {
        upstreamStatus = status;
        response = "SECRET PHOTO CONTENT";
        assertThatThrownBy(() -> client("key", 2000).describe(new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(MlException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(expected);
                    assertThat(error.getMessage()).doesNotContain("SECRET", "PHOTO");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{}", "null", "{\"item\":1,\"flags\":[]}",
            "{\"item\":\"Кружка\",\"flags\":[\"unknown\"]}",
            "{\"item\":\"Кружка\",\"flags\":[\"fragile\",\"fragile\"]}",
            "{\"item\":\"Кружка\",\"flags\":[],\"extra\":true}"})
    void rejectsInvalidModelJson(String json) throws Exception {
        response = envelope(json);
        expectCode("LLM_INVALID_RESPONSE");
    }

    @Test
    void rejectsMalformedEnvelopeAndTruncatedResponse() throws Exception {
        response = "not json";
        expectCode("LLM_INVALID_RESPONSE");
        response = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}";
        expectCode("LLM_INVALID_RESPONSE");
        response = envelope("{\"item\":\"" + "x".repeat(256) + "\",\"flags\":[]}");
        expectCode("LLM_INVALID_RESPONSE");
    }

    @Test
    void mapsRefusalAndEmptyNameToUnrecognized() throws Exception {
        response = envelope("{\"item\":\"\",\"flags\":[]}");
        expectCode("IMAGE_NOT_RECOGNIZED");
        response = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"refusal\":\"refused\"}}]}";
        expectCode("IMAGE_NOT_RECOGNIZED");
        response = "{\"choices\":[{\"finish_reason\":\"content_filter\"}]}";
        expectCode("IMAGE_NOT_RECOGNIZED");
    }

    @Test
    void enforcesDeadlineAndDoesNotRetry() throws Exception {
        delay = 500;
        var client = client("key", 100);
        assertThatThrownBy(() -> client.describe(new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(MlException.class, e -> assertThat(e.getCode()).isEqualTo("LLM_TIMEOUT"));
    }

    @Test
    void deadlineIncludesReadingTheResponseBody() throws Exception {
        bodyDelay = 2000;
        var client = client("key", 1000);
        assertThatThrownBy(() -> client.describe(new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(MlException.class, e -> assertThat(e.getCode()).isEqualTo("LLM_TIMEOUT"));
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsOversizedUpstreamResponse() throws Exception {
        response = "x".repeat(64 * 1024 + 1);
        expectCode("LLM_INVALID_RESPONSE");
    }

    @Test
    void overloadedExecutorIsUnavailable() throws Exception {
        worker.shutdownNow();
        expectCode("LLM_UNAVAILABLE");
        assertThat(calls.get()).isZero();
        assertThat(meters.get("movermate.llm.overload").tag("scenario", "single").counter().count()).isEqualTo(1);
        assertThat(meters.get("movermate.llm.operation").tags("scenario", "single", "outcome", "overload").timer().count()).isEqualTo(1);
        assertThat(meters.get("movermate.llm.active").gauge().value()).isZero();
    }

    @Test
    void missingKeyDoesNotSendRequest() throws Exception {
        assertThatThrownBy(() -> client("", 1000).describe(new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(MlException.class, e -> assertThat(e.getCode()).isEqualTo("LLM_UNAVAILABLE"));
        assertThat(request.get()).isNull();
    }

    @Test
    void networkFailureIsUnavailable() throws Exception {
        var client = client("key", 2000);
        server.stop(0);
        assertThatThrownBy(() -> client.describe(new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(MlException.class, e -> assertThat(e.getCode()).isEqualTo("LLM_UNAVAILABLE"));
    }

    @Test
    void countsActiveWorkerUntilItActuallyFinishesAndPropagatesRequestContext() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var capturedId = new AtomicReference<String>();
        var validator = new ItemDescriptionValidator(mapper) {
            @Override public ru.example.prodbackend.ml.dto.ItemDescriptionResponse validate(String body) {
                capturedId.set(org.slf4j.MDC.get("requestId"));
                entered.countDown();
                boolean released = false;
                while (!released) {
                    try { released = release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { /* Simulate an operation that ignores cancellation. */ }
                }
                return super.validate(body);
            }
        };
        var properties = new LlmProxyProperties(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + "/chat/completions"), "test-model", "secret", 1000, 1000);
        var client = new HttpLlmProxyClient(RestClient.create(), properties, worker, validator, meters);
        org.slf4j.MDC.put("requestId", "request-test");
        try {
            assertThatThrownBy(() -> client.describe(new byte[]{1}, "image/png"))
                    .isInstanceOfSatisfying(MlException.class, e -> assertThat(e.getCode()).isEqualTo("LLM_TIMEOUT"));
            assertThat(entered.getCount()).isZero();
            assertThat(capturedId.get()).isEqualTo("request-test");
            assertThat(meters.get("movermate.llm.active").gauge().value()).isEqualTo(1);
            assertThat(meters.get("movermate.llm.operation").tags("scenario", "single", "outcome", "timeout").timer().count()).isEqualTo(1);
        } finally {
            release.countDown();
            org.slf4j.MDC.clear();
        }
        assertThat(worker.submit(() -> org.slf4j.MDC.get("requestId")).get(2, TimeUnit.SECONDS)).isNull();
        assertThat(meters.get("movermate.llm.active").gauge().value()).isZero();
        assertThat(meters.get("movermate.llm.operation").tags("scenario", "single", "outcome", "success").timer().count()).isZero();
    }

    @Test
    void metricsDistinguishInvalidResponseAndUpstreamErrors() throws Exception {
        response = "bad json";
        expectCode("LLM_INVALID_RESPONSE");
        upstreamStatus = 503;
        expectCode("LLM_UNAVAILABLE");
        assertThat(meters.get("movermate.llm.operation").tags("scenario", "single", "outcome", "invalid_response").timer().count()).isEqualTo(1);
        assertThat(meters.get("movermate.llm.operation").tags("scenario", "single", "outcome", "upstream_error").timer().count()).isEqualTo(1);
        assertThat(meters.get("movermate.llm.active").gauge().value()).isZero();
    }

    private void expectCode(String code) throws Exception {
        var client = client("key", 2000);
        assertThatThrownBy(() -> client.describe(new byte[]{1}, "image/png"))
                .isInstanceOfSatisfying(MlException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private HttpLlmProxyClient client(String key, long timeout) throws Exception {
        var properties = new LlmProxyProperties(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + "/chat/completions"), "test-model", key, 1000, timeout);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofMillis(timeout));
        return new HttpLlmProxyClient(RestClient.builder().requestFactory(factory).build(), properties,
                worker, new ItemDescriptionValidator(mapper), meters);
    }

    private String envelope(String content) {
        return mapper.writeValueAsString(Map.of("choices", java.util.List.of(Map.of(
                "finish_reason", "stop", "message", Map.of("content", content)))));
    }
}

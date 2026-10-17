package ru.example.prodbackend.ml.client;

import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.example.prodbackend.item.entity.ItemFlag;
import ru.example.prodbackend.ml.config.LlmProxyProperties;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.dto.ItemsDescriptionResponse;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.validation.ItemDescriptionValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@Component
@Slf4j
public class HttpLlmProxyClient implements LlmProxyClient {
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private final MeterRegistry meters;
    private final AtomicInteger active = new AtomicInteger();
    private final RestClient restClient;
    private final LlmProxyProperties properties;
    private final ExecutorService executor;
    private final ItemDescriptionValidator validator;
    private final String prompt;
    private final String itemsPrompt;

    public HttpLlmProxyClient(@Qualifier("llmRestClient") RestClient restClient,
            LlmProxyProperties properties, @Qualifier("llmExecutor") ExecutorService executor,
            ItemDescriptionValidator validator, MeterRegistry meters) throws IOException {
        this.meters = meters;
        meters.gauge("movermate.llm.active", active);
        for (String scenario : List.of("single", "multiple")) {
            meters.counter("movermate.llm.overload", "scenario", scenario);
            for (String outcome : List.of("success", "timeout", "overload", "upstream_error",
                    "invalid_response", "not_recognized", "unavailable", "interrupted")) {
                meters.timer("movermate.llm.operation", "scenario", scenario, "outcome", outcome);
            }
        }
        this.restClient = restClient;
        this.properties = properties;
        this.executor = executor;
        this.validator = validator;
        this.prompt = new ClassPathResource("ml/item-description-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        this.itemsPrompt = new ClassPathResource("ml/items-description-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public ItemDescriptionResponse describe(byte[] image, String mime) {
        return execute("single", () -> validator.validate(request(image, mime, false)));
    }

    @Override
    public ItemsDescriptionResponse describeItems(byte[] image, String mime) {
        return execute("multiple", () -> validator.validateItems(request(image, mime, true)));
    }

    private <T> T execute(String scenario, Callable<T> operation) {
        long started = System.nanoTime();
        Future<T> pending = null;
        String outcome = "unavailable";
        Map<String, String> context = MDC.getCopyOfContextMap();
        try {
            if (properties.apiKey() == null || properties.apiKey().isBlank()) throw unavailable();
            pending = executor.submit(() -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                active.incrementAndGet();
                try {
                    if (context == null) MDC.clear(); else MDC.setContextMap(context);
                    return operation.call();
                } finally {
                    active.decrementAndGet();
                    if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
                }
            });
            T result = pending.get(properties.timeoutMs(), TimeUnit.MILLISECONDS);
            outcome = "success";
            return result;
        } catch (TimeoutException e) {
            outcome = "timeout";
            throw logged(new MlException(HttpStatus.GATEWAY_TIMEOUT, "LLM_TIMEOUT",
                    "Превышено время ожидания LLM Proxy"), started);
        } catch (InterruptedException e) {
            outcome = "interrupted";
            Thread.currentThread().interrupt();
            throw logged(unavailable(), started);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof MlException error) {
                outcome = switch (error.getCode()) {
                    case "LLM_TIMEOUT" -> "timeout";
                    case "LLM_INVALID_RESPONSE" -> "invalid_response";
                    case "IMAGE_NOT_RECOGNIZED" -> "not_recognized";
                    default -> "upstream_error";
                };
                throw logged(error, started);
            }
            if (hasTimeout(cause)) {
                outcome = "timeout";
                throw logged(new MlException(HttpStatus.GATEWAY_TIMEOUT,
                        "LLM_TIMEOUT", "Превышено время ожидания LLM Proxy"), started);
            }
            outcome = "upstream_error";
            throw logged(unavailable(), started);
        } catch (RejectedExecutionException e) {
            outcome = "overload";
            meters.counter("movermate.llm.overload", "scenario", scenario).increment();
            throw logged(unavailable(), started);
        } catch (MlException e) {
            throw logged(e, started);
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
            meters.timer("movermate.llm.operation", "scenario", scenario, "outcome", outcome)
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            log.atInfo().addKeyValue("event", "llm_operation_completed")
                    .addKeyValue("scenario", scenario).addKeyValue("outcome", outcome)
                    .addKeyValue("durationMs", elapsed(started)).log("LLM operation completed");
        }
    }

    private String request(byte[] image, String mime, boolean multiple) {
        return restClient.post().uri(properties.url())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(properties.apiKey().strip()))
                .body(payload(image, mime, multiple))
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status < 200 || status >= 300) {
                        // Never read/log upstream error bodies: they can contain the original photo/key.
                        if (status == 401 || status == 403 || status == 429 || status >= 500) throw unavailable();
                        throw new MlException(HttpStatus.BAD_GATEWAY, "LLM_UPSTREAM_ERROR", "Ошибка LLM Proxy");
                    }
                    byte[] bytes = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                    if (bytes.length > MAX_RESPONSE_BYTES) throw new MlException(HttpStatus.BAD_GATEWAY,
                            "LLM_INVALID_RESPONSE", "Ответ LLM Proxy слишком большой");
                    return new String(bytes, StandardCharsets.UTF_8);
                });
    }

    private Map<String, Object> payload(byte[] image, String mime, boolean multiple) {
        Map<String, Object> itemSchema = Map.of(
                "type", "object", "properties", Map.of(
                        "item", Map.of("type", "string", "maxLength", 255),
                        "flags", Map.of("type", "array", "items", Map.of("type", "string", "enum",
                                Arrays.stream(ItemFlag.values()).map(ItemFlag::value).toList()))),
                "required", List.of("item", "flags"), "additionalProperties", false);
        Map<String, Object> schema = multiple ? Map.of(
                "type", "object", "properties", Map.of("items", Map.of(
                        "type", "array", "maxItems", ItemDescriptionValidator.MAX_ITEMS, "items", itemSchema)),
                "required", List.of("items"), "additionalProperties", false) : itemSchema;
        return Map.of("model", properties.model(), "messages", List.of(
                Map.of("role", "system", "content", multiple ? itemsPrompt : prompt),
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", multiple
                                ? "Составь список уверенно распознанных предметов на фотографии."
                                : "Составь опись главного предмета на фотографии."),
                        Map.of("type", "image_url", "image_url", Map.of("url",
                                "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image)))))),
                "response_format", Map.of("type", "json_schema", "json_schema", Map.of(
                        "name", multiple ? "items_description" : "item_description", "strict", true, "schema", schema)));
    }

    private static boolean hasTimeout(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.net.SocketTimeoutException) return true;
        }
        return false;
    }
    private static long elapsed(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }
    private MlException logged(MlException error, long started) {
        log.atWarn().addKeyValue("event", "llm_operation_error")
                .addKeyValue("durationMs", elapsed(started)).addKeyValue("code", error.getCode())
                .addKeyValue("traceId", error.getTraceId().toString()).log("LLM operation failed");
        return error;
    }
    private MlException unavailable() {
        return new MlException(HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE", "LLM Proxy временно недоступен");
    }
}

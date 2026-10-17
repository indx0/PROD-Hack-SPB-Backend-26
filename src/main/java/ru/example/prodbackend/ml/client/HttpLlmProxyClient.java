package ru.example.prodbackend.ml.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.example.prodbackend.item.entity.ItemFlag;
import ru.example.prodbackend.ml.config.LlmProxyProperties;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
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
    private final RestClient restClient;
    private final LlmProxyProperties properties;
    private final ExecutorService executor;
    private final ItemDescriptionValidator validator;
    private final String prompt;

    public HttpLlmProxyClient(@Qualifier("llmRestClient") RestClient restClient,
            LlmProxyProperties properties, @Qualifier("llmExecutor") ExecutorService executor,
            ItemDescriptionValidator validator) throws IOException {
        this.restClient = restClient;
        this.properties = properties;
        this.executor = executor;
        this.validator = validator;
        this.prompt = new ClassPathResource("ml/item-description-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public ItemDescriptionResponse describe(byte[] image, String mime) {
        long started = System.nanoTime();
        Future<ItemDescriptionResponse> pending = null;
        try {
            if (properties.apiKey() == null || properties.apiKey().isBlank()) throw unavailable();
            pending = executor.submit(() -> request(image, mime));
            ItemDescriptionResponse result = pending.get(properties.timeoutMs(), TimeUnit.MILLISECONDS);
            log.info("LLM description model={} durationMs={} outcome=success", properties.model(), elapsed(started));
            return result;
        } catch (TimeoutException e) {
            throw logged(new MlException(HttpStatus.GATEWAY_TIMEOUT, "LLM_TIMEOUT",
                    "Превышено время ожидания LLM Proxy"), started);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw logged(unavailable(), started);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof MlException error) throw logged(error, started);
            if (hasTimeout(cause)) throw logged(new MlException(HttpStatus.GATEWAY_TIMEOUT,
                    "LLM_TIMEOUT", "Превышено время ожидания LLM Proxy"), started);
            throw logged(unavailable(), started);
        } catch (RejectedExecutionException e) {
            throw logged(unavailable(), started);
        } catch (MlException e) {
            throw logged(e, started);
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    private ItemDescriptionResponse request(byte[] image, String mime) {
        String body = restClient.post().uri(properties.url())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(properties.apiKey().strip()))
                .body(payload(image, mime))
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
        return validator.validate(body);
    }

    private Map<String, Object> payload(byte[] image, String mime) {
        List<String> flags = Arrays.stream(ItemFlag.values()).map(ItemFlag::value).toList();
        return Map.of("model", properties.model(), "messages", List.of(
                Map.of("role", "system", "content", prompt),
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", "Составь опись главного предмета на фотографии."),
                        Map.of("type", "image_url", "image_url", Map.of("url",
                                "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image)))))),
                "response_format", Map.of("type", "json_schema", "json_schema", Map.of(
                        "name", "item_description", "strict", true, "schema", Map.of(
                                "type", "object", "properties", Map.of(
                                        "item", Map.of("type", "string"),
                                        "flags", Map.of("type", "array", "items", Map.of("type", "string", "enum", flags))),
                                "required", List.of("item", "flags"), "additionalProperties", false))));
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
        log.warn("LLM description model={} durationMs={} outcome={} traceId={}",
                properties.model(), elapsed(started), error.getCode(), error.getTraceId());
        return error;
    }
    private MlException unavailable() {
        return new MlException(HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE", "LLM Proxy временно недоступен");
    }
}

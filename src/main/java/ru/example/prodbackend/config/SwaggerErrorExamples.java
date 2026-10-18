package ru.example.prodbackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Примеры известных ответов обработчиков. Не изменяет ответы работающего API. */
final class SwaggerErrorExamples {
    private static final Pattern CODE = Pattern.compile("(?:^|;\\s*)([A-Z][A-Z_]+):\\s*([^;]+)");
    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("INVALID_REQUEST_BODY", "Request body is missing or contains invalid JSON"),
            Map.entry("INVALID_REQUEST_PARAMETER", "Invalid format for parameter: projectId"),
            Map.entry("UNAUTHORIZED", "Authentication required or access token is invalid"),
            Map.entry("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired"),
            Map.entry("VALIDATION_ERROR", "Request validation failed"),
            Map.entry("INVALID_PHOTO", "Не удалось прочитать фотографию"),
            Map.entry("EMPTY_IMAGE", "Фотография пуста"),
            Map.entry("IMAGE_TOO_LARGE", "Размер фотографии превышает 10 МиБ"),
            Map.entry("UNSUPPORTED_IMAGE_FORMAT", "Используйте JPEG, PNG или WebP"),
            Map.entry("UNSUPPORTED_MEDIA_TYPE", "Неподдерживаемый Content-Type"),
            Map.entry("LLM_UNAVAILABLE", "LLM Proxy временно недоступен"),
            Map.entry("LLM_TIMEOUT", "Превышено время ожидания LLM Proxy"),
            Map.entry("LLM_UPSTREAM_ERROR", "Ошибка LLM Proxy"),
            Map.entry("LLM_INVALID_RESPONSE", "Ответ LLM Proxy слишком большой")
    );

    private SwaggerErrorExamples() { }

    static void apply(OpenAPI openApi) {
        if (openApi.getPaths() == null) return;
        openApi.getPaths().forEach((url, path) -> path.readOperations().forEach(operation -> {
            if (operation.getResponses() == null) return;
            operation.getResponses().forEach((status, response) -> {
                if (!status.matches("[45]\\d\\d") || response.getContent() == null
                        || response.getDescription() == null) return;
                response.getContent().values().forEach(media -> {
                    if (!hasErrorSchema(media.getSchema())) return;
                    // Springdoc may merge the success return type into errors of overloaded JSON/multipart handlers.
                    media.setSchema(new Schema<>().$ref("#/components/schemas/ErrorResponse"));
                    var codes = CODE.matcher(response.getDescription());
                    while (codes.find()) {
                        String code = codes.group(1);
                        String message = MESSAGES.get(code);
                        if (message == null) continue;
                        // A parameter example must refer to a parameter actually present on this operation.
                        String parameter = operation.getParameters() == null ? null : operation.getParameters().stream()
                                .filter(p -> p.getSchema() != null && "uuid".equals(p.getSchema().getFormat()))
                                .map(Parameter::getName).findFirst().orElse(null);
                        if ("INVALID_REQUEST_PARAMETER".equals(code)) {
                            if (parameter == null) continue;
                            message = "Invalid format for parameter: " + parameter;
                        }
                        // Validation messages depend on DTO constraints; use a known concrete example only.
                        if ("VALIDATION_ERROR".equals(code) && !"/api/auth/register".equals(url)) continue;
                        var value = new LinkedHashMap<String, Object>();
                        value.put("code", code);
                        if ("UNAUTHORIZED".equals(code) && "/api/auth/authenticate".equals(url)) {
                            message = "Invalid email or password";
                        }
                        value.put("message", message);
                        value.put("traceId", "550e8400-e29b-41d4-a716-446655440000");
                        value.put("time", "2026-11-01T09:00:00Z");
                        value.put("fieldErrors", switch (code) {
                            case "INVALID_REQUEST_PARAMETER" -> List.of(Map.of("field", parameter, "issue", "Invalid parameter format"));
                            case "VALIDATION_ERROR" -> List.of(Map.of("field", "email", "issue", "email is blank"));
                            default -> null;
                        });
                        media.addExamples(code, new Example().summary(codes.group(2)).value(value));
                    }
                });
            });
        }));
    }


    private static boolean hasErrorSchema(Schema<?> schema) {
        return schema != null && ("#/components/schemas/ErrorResponse".equals(schema.get$ref())
                || schema.getOneOf() != null && schema.getOneOf().stream().anyMatch(SwaggerErrorExamples::hasErrorSchema));
    }
}

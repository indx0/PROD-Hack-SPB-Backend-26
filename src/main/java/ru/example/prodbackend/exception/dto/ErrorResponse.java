package ru.example.prodbackend.exception.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {
    @Schema(example = "VALIDATION_ERROR", description = "Машинный код ошибки на английском в формате UPPER_SNAKE_CASE")
    private String code;
    @Schema(example = "Request validation failed", description = "Сообщение сервера об ошибке; язык зависит от источника ошибки")
    private String message;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор ошибки для поиска в журналах")
    private UUID traceId;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время ошибки в UTC")
    private Instant time;
    @Schema(description = "Ошибки отдельных полей; null, если детализация отсутствует")
    private List<FieldErrorResponse> fieldErrors;

}

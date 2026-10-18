package ru.example.prodbackend.taskset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSetSummaryResponse {

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Уникальный идентификатор записи")
    private UUID id;

    @Schema(example = "Подготовка кухни", description = "Название личного набора задач")
    private String name;

    @Schema(example = "Подготовить вещи к переезду", description = "Описание; может отсутствовать")
    private String description;

    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время создания в UTC")
    private Instant createdAt;

    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время последнего изменения в UTC")
    private Instant updatedAt;
}

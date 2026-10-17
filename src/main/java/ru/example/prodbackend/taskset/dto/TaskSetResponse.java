package ru.example.prodbackend.taskset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskSetResponse(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Уникальный идентификатор записи")
        UUID id,
        @Schema(example = "Подготовка кухни", description = "Название личного набора задач")
        String name,
        @Schema(example = "Подготовить вещи к переезду", description = "Описание; может отсутствовать")
        String description,
        @Schema(description = "Задачи набора в порядке позиций")
        List<TaskSetItemResponse> items,
        @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время создания в UTC")
        Instant createdAt,
        @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время последнего изменения в UTC")
        Instant updatedAt) { }

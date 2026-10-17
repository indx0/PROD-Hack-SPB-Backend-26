package ru.example.prodbackend.taskset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import ru.example.prodbackend.task.entity.TaskStage;

public record TaskSetItemResponse(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Уникальный идентификатор записи")
        UUID id,
        @Schema(example = "Упаковать кухню", description = "Название задачи")
        String title,
        @Schema(example = "Подготовить вещи к переезду", description = "Описание; может отсутствовать")
        String description,
        @Schema(description = "Этап переезда: BEFORE_MOVE — до переезда, MOVING_DAY — день переезда, AFTER_MOVE — после переезда")
TaskStage stage,
        @Schema(example = "-2", description = "Смещение срока в календарных днях относительно даты переезда в UTC; отрицательное — до переезда")
Integer offsetDays,
        @Schema(example = "0", description = "Позиция задачи в наборе, начиная с 0")
int position) { }

package ru.example.prodbackend.taskset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import ru.example.prodbackend.task.entity.TaskStage;

public record TaskSetItemRequest(
        @Schema(example = "Упаковать кухню", description = "Название задачи")
@NotBlank @Size(max = 255) String title,
        @Schema(example = "Подготовить вещи к переезду", description = "Описание; может отсутствовать")
@Size(max = 10000) String description,
        @Schema(description = "Этап переезда: BEFORE_MOVE — до переезда, MOVING_DAY — день переезда, AFTER_MOVE — после переезда")
@NotNull TaskStage stage,
        @Schema(example = "-2", description = "Смещение срока в календарных днях относительно даты переезда в UTC; отрицательное — до переезда")
@NotNull Integer offsetDays) { }

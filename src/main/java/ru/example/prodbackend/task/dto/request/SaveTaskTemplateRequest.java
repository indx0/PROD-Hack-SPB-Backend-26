package ru.example.prodbackend.task.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.example.prodbackend.task.entity.TaskStage;

public record SaveTaskTemplateRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 10000) String description,
        @NotNull TaskStage stage,
        @NotNull @Schema(description = "Срок относительно даты переезда: -2 — за два дня, 0 — в день переезда", example = "-2") Integer dueDateOffsetDays) {
}

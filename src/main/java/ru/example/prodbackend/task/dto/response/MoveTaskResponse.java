package ru.example.prodbackend.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.entity.TaskDueType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveTaskResponse {

    private UUID id;
    private UUID projectId;
    private String title;
    private String description;
    private TaskStage stage;
    private LocalDate dueDate;
    @Schema(description = "FIXED — фиксированный срок или без срока; RELATIVE — сохранённый срок относительно переезда")
    private TaskDueType dueType;
    @Schema(description = "Смещение в календарных днях для следующего явного пересчёта; только для RELATIVE", example = "-2")
    private Integer offsetDays;
    private String assigneeName;
    private boolean completed;
    private Instant completedAt;

    @Schema(description = "Исходный шаблон; null для собственной задачи")
    private UUID templateId;
    @Schema(description = "Запись применения личного набора; null для обычной задачи или общего шаблона")
    private UUID taskSetApplicationId;

    private Instant createdAt;
    private Instant updatedAt;
}

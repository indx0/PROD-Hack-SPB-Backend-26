package ru.example.prodbackend.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import ru.example.prodbackend.task.entity.TaskStage;

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
    private String assigneeName;
    private boolean completed;
    private Instant completedAt;

    @Schema(description = "Исходный шаблон; null для собственной задачи")
    private UUID templateId;

    private Instant createdAt;
    private Instant updatedAt;
}

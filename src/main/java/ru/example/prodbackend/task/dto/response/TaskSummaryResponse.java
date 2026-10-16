package ru.example.prodbackend.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSummaryResponse {
    @Schema(description = "Общее количество задач проекта по всем этапам", example = "11")
    private long totalTasks;

    @Schema(description = "Количество задач с заполненным completedAt", example = "4")
    private long completedTasks;
}

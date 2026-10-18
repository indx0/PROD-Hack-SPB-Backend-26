package ru.example.prodbackend.taskset.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.task.dto.response.MoveTaskResponse;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSetApplicationResponse {

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Уникальный идентификатор записи")
    private UUID id;

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор проекта переезда")
    private UUID projectId;

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор личного набора задач")
    private UUID taskSetId;

    @Schema(description = "Название набора на момент применения")
    private String taskSetName;

    @Schema(types = {"string", "null"}, format = "uuid", description = "Неизменяемый UUID первоначальной папки применения; null — без папки. Сохраняется после удаления папки и может отличаться от текущих folderId задач. Не доказывает существование папки сейчас")
    private UUID folderId;

    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время применения набора в UTC")
    private Instant appliedAt;

    @Schema(description = "Оставшиеся задачи применения в исходном порядке; удалённые задачи не восстанавливаются")
    private List<MoveTaskResponse> tasks;
}

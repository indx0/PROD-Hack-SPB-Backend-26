package ru.example.prodbackend.taskfolder.dto;

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
public class TaskFolderResponse {

    @Schema(description = "UUID папки")
    private UUID id;

    @Schema(description = "UUID переезда")
    private UUID projectId;

    @Schema(description = "Пользовательское название папки", example = "Документы")
    private String name;

    @Schema(allowableValues = {"mint", "cyan", "coral", "sand", "lilac", "forest"}, description = "Ключ цвета папки")
    private String colorKey;

    @Schema(description = "Количество реальных задач папки по всем этапам", minimum = "0")
    private long taskCount;

    @Schema(description = "Количество задач папки с заполненным completedAt", minimum = "0")
    private long completedTaskCount;

    @Schema(description = "Время создания в UTC")
    private Instant createdAt;

    @Schema(description = "Время последнего изменения имени или цвета в UTC")
    private Instant updatedAt;
}

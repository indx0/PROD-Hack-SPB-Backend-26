package ru.example.prodbackend.taskfolder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record TaskFolderResponse(
        @Schema(description = "UUID папки") UUID id,
        @Schema(description = "UUID переезда") UUID projectId,
        @Schema(description = "Пользовательское название папки", example = "Документы") String name,
        @Schema(allowableValues = {"mint", "cyan", "coral", "sand", "lilac", "forest"}, description = "Ключ цвета папки") String colorKey,
        @Schema(description = "Количество реальных задач папки по всем этапам", minimum = "0") long taskCount,
        @Schema(description = "Количество задач папки с заполненным completedAt", minimum = "0") long completedTaskCount,
        @Schema(description = "Время создания в UTC") Instant createdAt,
        @Schema(description = "Время последнего изменения имени или цвета в UTC") Instant updatedAt) { }

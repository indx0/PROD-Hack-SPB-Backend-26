package ru.example.prodbackend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEventResponse {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Уникальный идентификатор записи")
    private UUID id;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор проекта переезда")
    private UUID projectId;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор пользователя, выполнившего действие")
    private UUID actorId;
    @Schema(description = "Тип события журнала проекта")
    private ProjectEventType eventType;
    @Schema(description = "Тип объекта события")
    private EventObjectType objectType;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор объекта; история сохраняется после его удаления")
    private UUID objectId;
    @Schema(example = "Подготовить вещи к переезду", description = "Описание действия пользователя")
    private String description;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время события в UTC")
    private Instant occurredAt;
    @Schema(description = "Исходный статус при BOX_STATUS_CORRECTED; у остальных событий null", nullable = true)
    private BoxStatus previousStatus;
    @Schema(description = "Новый статус при BOX_STATUS_CORRECTED; у остальных событий null", nullable = true)
    private BoxStatus newStatus;
}

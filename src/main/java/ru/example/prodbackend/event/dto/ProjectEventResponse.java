package ru.example.prodbackend.event.dto;

import lombok.*;
import io.swagger.v3.oas.annotations.media.Schema;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventType;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEventResponse {

    private UUID id;
    private UUID projectId;
    private UUID actorId;
    private ProjectEventType eventType;
    private EventObjectType objectType;
    private UUID objectId;
    private String description;
    private Instant occurredAt;
    @Schema(description = "Исходный статус при BOX_STATUS_CORRECTED; у остальных событий null", nullable = true)
    private BoxStatus previousStatus;
    @Schema(description = "Новый статус при BOX_STATUS_CORRECTED; у остальных событий null", nullable = true)
    private BoxStatus newStatus;
}

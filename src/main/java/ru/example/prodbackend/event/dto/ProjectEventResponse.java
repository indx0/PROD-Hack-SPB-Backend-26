package ru.example.prodbackend.event.dto;

import lombok.*;
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
}

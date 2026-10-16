package ru.example.prodbackend.checklist.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistItemResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private boolean completed;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
}

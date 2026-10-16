package ru.example.prodbackend.box.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.box.entity.BoxStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoxResponse {
    private UUID id;

    private UUID projectId;

    private int number;

    private UUID roomId;

    private String roomName;

    private BoxStatus status;

    private boolean contentsDeferred;

    private Instant createdAt;

    private Instant updatedAt;
}
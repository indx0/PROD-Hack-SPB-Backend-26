package ru.example.prodbackend.box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "Количество записей вещей, без суммирования quantity")
    private long totalItems;
    private long packedItems;
    private long unpackedItems;
    @Schema(description = "Статус PACKING, коробка непустая и все вещи отмечены упакованными")
    private boolean readyToPack;
    @Schema(description = "Статус DELIVERED, коробка непустая и все вещи отмечены распакованными")
    private boolean readyToUnpack;

    private Instant createdAt;

    private Instant updatedAt;
}

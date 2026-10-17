package ru.example.prodbackend.item.dto;

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
public class ItemResponse {
    private UUID id;
    private String name;
    private int quantity;
    private boolean firstDay;
    private boolean lost;
    private boolean packed;
    private Instant packedAt;
    private boolean unpacked;
    private Instant unpackedAt;
    private UUID projectId;
    private UUID boxId;
    private int boxNumber;
    private UUID roomId;
    private String roomName;
    @Schema(types = {"string", "null"}, description = "Актуальный ключ значка комнаты; null — стандартное оформление")
    private String roomIconKey;
    private BoxStatus boxStatus;
    private Instant createdAt;
    private Instant updatedAt;
}

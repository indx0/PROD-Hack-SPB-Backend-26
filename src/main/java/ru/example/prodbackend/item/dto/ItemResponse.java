package ru.example.prodbackend.item.dto;

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
    private java.util.List<ru.example.prodbackend.item.entity.ItemFlag> flags;
    private boolean lost;
    private UUID projectId;
    private UUID boxId;
    private int boxNumber;
    private UUID roomId;
    private String roomName;
    private BoxStatus boxStatus;
    private Instant createdAt;
    private Instant updatedAt;
}

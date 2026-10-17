package ru.example.prodbackend.checklist.dto.response;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ChecklistItemResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private boolean completed;
    private Instant completedAt;
    @Schema(description = "UUID связанной вещи; null для самостоятельного пункта")
    private UUID itemId;
    private String itemName;
    private UUID boxId;
    private Integer boxNumber;
    private UUID roomId;
    private String roomName;
    private Instant createdAt;
    private Instant updatedAt;
}

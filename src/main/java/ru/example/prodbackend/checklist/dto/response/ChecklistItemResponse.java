package ru.example.prodbackend.checklist.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ChecklistItemResponse {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Уникальный идентификатор записи")
    private UUID id;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор проекта переезда")
    private UUID projectId;
    @Schema(example = "Посуда", description = "Название")
    private String name;
    @Schema(description = "Признак выполнения")
    private boolean completed;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время выполнения в UTC; null для невыполненной записи")
    private Instant completedAt;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "UUID связанной вещи; null для самостоятельного пункта")
    private UUID itemId;
    @Schema(description = "Актуальное название связанной вещи; null, если пункт не связан с вещью")
    private String itemName;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор коробки; null, если пункт не связан с вещью")
    private UUID boxId;
    @Schema(example = "1", description = "Номер коробки внутри проекта; null, если пункт не связан с вещью")
    private Integer boxNumber;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор комнаты назначения; null, если пункт не связан с вещью")
    private UUID roomId;
    @Schema(example = "Кухня", description = "Название комнаты назначения; null, если пункт не связан с вещью")
    private String roomName;
    @Schema(types = {"string", "null"}, description = "Актуальный ключ значка комнаты; null — без значка или без связи с вещью")
    private String roomIconKey;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время создания в UTC")
    private Instant createdAt;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время последнего изменения в UTC")
    private Instant updatedAt;
}

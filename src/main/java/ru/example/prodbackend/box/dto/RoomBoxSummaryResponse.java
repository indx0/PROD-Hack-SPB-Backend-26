package ru.example.prodbackend.box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.box.entity.BoxStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomBoxSummaryResponse {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор комнаты назначения")
    private UUID roomId;
    @Schema(example = "Кухня", description = "Название комнаты назначения")
    private String roomName;

    @Schema(types = {"string", "null"}, description = "Актуальный ключ значка комнаты; null — стандартное оформление")
    private String roomIconKey;
    @Schema(description = "Общее количество коробок")
    private long totalBoxes;
    @Schema(description = "Количество коробок по каждому статусу")
    private Map<BoxStatus, Long> countsByStatus;
    @Schema(description = "Количество коробок со статусом UNPACKED")
    private long unpackedBoxes;
    @Schema(description = "Доля распакованных коробок в процентах, от 0 до 100")
    private int unpackingProgressPercent;
}

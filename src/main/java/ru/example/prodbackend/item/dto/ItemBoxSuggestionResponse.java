package ru.example.prodbackend.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class ItemBoxSuggestionResponse {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор коробки")
    private UUID boxId;
    @Schema(example = "1", description = "Номер коробки внутри проекта")
    private int boxNumber;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор комнаты назначения")
    private UUID roomId;
    @Schema(example = "Кухня", description = "Название комнаты назначения")
    private String roomName;
    @Schema(types = {"string", "null"}, description = "Актуальный ключ значка комнаты; null — стандартное оформление")
    private String roomIconKey;
    @Schema(description = "Текущий статус коробки")
    private BoxStatus status;
    @Schema(description = "В содержимом коробки найдено совпадение названия вещи")
    private boolean contentsMatch;
    @Schema(description = "Комната коробки совпадает с комнатой искомой вещи")
    private boolean sameRoom;
}

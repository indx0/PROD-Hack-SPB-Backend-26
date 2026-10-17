package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Рекомендательная сверка с текущим снимком описи; результат не сохраняется")
public record UnpackingVerificationResponse(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор коробки")
        UUID boxId,
        @Schema(description = "Вещи, сопоставленные с текущей описью")
        List<MatchedItem> matched,
        @Schema(description = "Не обнаружены на данном фото; это не подтверждение потери")
        List<InventoryItem> notSeen,
        @Schema(description = "Распознаны, но не сопоставлены с описью; не обязательно лишние вещи")
        List<String> unmatched) {
    public record MatchedItem(
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор вещи")
        UUID itemId,
        @Schema(example = "Посуда", description = "Название")
        String name,
        @Schema(example = "Белая керамическая кружка", description = "Описание предмета на фотографии")
        String photoDescription) { }
}

package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Рекомендательная сверка с текущим снимком описи; результат не сохраняется")
public record UnpackingVerificationResponse(
        UUID boxId,
        List<MatchedItem> matched,
        @Schema(description = "Не обнаружены на данном фото; это не подтверждение потери")
        List<InventoryItem> notSeen,
        @Schema(description = "Распознаны, но не сопоставлены с описью; не обязательно лишние вещи")
        List<String> unmatched) {
    public record MatchedItem(UUID itemId, String name, String photoDescription) { }
}

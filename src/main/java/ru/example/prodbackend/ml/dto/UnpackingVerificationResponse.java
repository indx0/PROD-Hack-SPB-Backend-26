package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Рекомендательная сверка с текущим снимком описи; результат не сохраняется")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnpackingVerificationResponse {

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор коробки")
    private UUID boxId;

    @Schema(description = "Вещи, сопоставленные с текущей описью")
    private List<MatchedItem> matched;

    @Schema(description = "Не обнаружены на данном фото; это не подтверждение потери")
    private List<InventoryItem> notSeen;

    @Schema(description = "Распознаны, но не сопоставлены с описью; не обязательно лишние вещи")
    private List<String> unmatched;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchedItem {

        @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор вещи")
        private UUID itemId;

        @Schema(example = "Посуда", description = "Название")
        private String name;

        @Schema(example = "Белая керамическая кружка", description = "Описание предмета на фотографии")
        private String photoDescription;
    }
}

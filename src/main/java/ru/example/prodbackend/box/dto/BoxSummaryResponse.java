package ru.example.prodbackend.box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.box.entity.BoxStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoxSummaryResponse {
    @Schema(description = "Общее количество коробок")
    private long totalBoxes;
    @Schema(description = "Количество коробок по каждому статусу")
    private Map<BoxStatus, Long> countsByStatus;
    @Schema(description = "Количество коробок с отложенным заполнением содержимого")
    private long deferredContentsCount;
    @Schema(description = "Сводка по комнатам назначения")
    private List<RoomBoxSummaryResponse> rooms;
}

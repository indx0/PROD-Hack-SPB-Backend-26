package ru.example.prodbackend.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.item.entity.ItemFlag;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemResponse {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Уникальный идентификатор записи")
    private UUID id;
    @Schema(example = "Посуда", description = "Название")
    private String name;
    @Schema(example = "2", description = "Количество одинаковых предметов в записи")
    private int quantity;
    @Schema(description = "Вещь нужна в первый день после переезда")
    private boolean firstDay;
    @Schema(description = "Метки вещи: fragile — хрупкое, first_day — первый день, carry_personally — везти с собой")
    private List<ItemFlag> flags;
    @Schema(description = "Вещь помечена потерянной")
    private boolean lost;
    @Schema(description = "Все предметы записи отмечены упакованными")
    private boolean packed;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время отметки упаковки в UTC; null, если отметка отсутствует")
    private Instant packedAt;
    @Schema(description = "Все предметы записи отмечены распакованными")
    private boolean unpacked;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время отметки распаковки в UTC; null, если отметка отсутствует")
    private Instant unpackedAt;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор проекта переезда")
    private UUID projectId;
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
    private BoxStatus boxStatus;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время создания в UTC")
    private Instant createdAt;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время последнего изменения в UTC")
    private Instant updatedAt;
}

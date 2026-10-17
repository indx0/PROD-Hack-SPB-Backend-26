package ru.example.prodbackend.box.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
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
public class BoxResponse {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Уникальный идентификатор записи")
    private UUID id;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор проекта переезда")
    private UUID projectId;
    @Schema(example = "1", description = "Положительный номер коробки, уникальный внутри проекта")
    private int number;
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор комнаты назначения")
    private UUID roomId;
    @Schema(example = "Кухня", description = "Название комнаты назначения")
    private String roomName;

    @Schema(types = {"string", "null"}, description = "Актуальный ключ значка комнаты; null — стандартное оформление")
    private String roomIconKey;
    @Schema(description = "Текущий статус коробки")
    private BoxStatus status;
    @Schema(description = "Заполнение содержимого коробки отложено")
    private boolean contentsDeferred;

    @Schema(example = "Упаковать кухню", description = "Пользовательская подпись; без подписи — пустая строка")
    private String title;
    @Schema(description = "Хрупкое содержимое")
    private boolean fragile;
    @Schema(description = "Независимый от вещей и чек-листа признак коробки первого дня")
    private boolean firstDay;
    @Schema(description = "Пользователь перевозит коробку с собой")
    private boolean carryWithMe;

    @Schema(description = "Количество записей вещей, без суммирования quantity")
    private long totalItems;
    @Schema(description = "Количество записей вещей с отметкой упаковки, без суммирования quantity")
    private long packedItems;
    @Schema(description = "Количество записей вещей с отметкой распаковки, без суммирования quantity")
    private long unpackedItems;
    @Schema(description = "Статус PACKING, коробка непустая и все вещи отмечены упакованными")
    private boolean readyToPack;
    @Schema(description = "Статус DELIVERED, коробка непустая и все вещи отмечены распакованными")
    private boolean readyToUnpack;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время создания в UTC")
    private Instant createdAt;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время последнего изменения в UTC")
    private Instant updatedAt;
}

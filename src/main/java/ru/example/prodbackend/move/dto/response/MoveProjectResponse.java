package ru.example.prodbackend.move.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveProjectResponse {

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор проекта переезда")
    private UUID id;

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "UUID создателя; все добавленные участники имеют те же права")
    private UUID ownerId;

    @Schema(example = "Посуда", description = "Название переезда")
    private String name;

    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата и время переезда")
    private Instant moveDate;

    @Schema(example = "Москва, ул. Примерная, д. 1", description = "Адрес отправления; может быть null")
    private String originAddress;

    @Schema(example = "Москва, ул. Новая, д. 2", description = "Адрес назначения; может быть null")
    private String destinationAddress;

    @Schema(description = "Проект в архиве: доступен для чтения, изменения запрещены до восстановления")
    private boolean archived;

    @Schema(example = "2026-11-01T09:00:00Z", types = {"string", "null"}, format = "date-time", description = "Время архивации; null для активного проекта")
    private Instant archivedAt;

    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата создания проекта")
    private Instant createdAt;

    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата последнего изменения проекта")
    private Instant updatedAt;
}

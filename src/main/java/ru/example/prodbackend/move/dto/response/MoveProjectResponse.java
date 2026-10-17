package ru.example.prodbackend.move.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoveProjectResponse {

    @Schema(description = "Идентификатор проекта переезда")
    private UUID id;

    @Schema(description = "UUID создателя; все добавленные участники имеют те же права")
    private UUID ownerId;

    @Schema(description = "Название переезда")
    private String name;

    @Schema(description = "Дата и время переезда")
    private Instant moveDate;

    @Schema(description = "Адрес отправления; может быть null")
    private String originAddress;

    @Schema(description = "Адрес назначения; может быть null")
    private String destinationAddress;

    @Schema(description = "Проект в архиве: доступен для чтения, изменения запрещены до восстановления")
    private boolean archived;

    @Schema(types = {"string", "null"}, format = "date-time", description = "Время архивации; null для активного проекта")
    private Instant archivedAt;

    @Schema(description = "Дата создания проекта")
    private Instant createdAt;

    @Schema(description = "Дата последнего изменения проекта")
    private Instant updatedAt;
}

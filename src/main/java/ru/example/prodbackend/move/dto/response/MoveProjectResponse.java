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

    @Schema(description = "Название переезда")
    private String name;

    @Schema(description = "Дата и время переезда")
    private Instant moveDate;

    @Schema(description = "Адрес отправления; может быть null")
    private String originAddress;

    @Schema(description = "Адрес назначения; может быть null")
    private String destinationAddress;

    @Schema(description = "Дата создания проекта")
    private Instant createdAt;

    @Schema(description = "Дата последнего изменения проекта")
    private Instant updatedAt;
}
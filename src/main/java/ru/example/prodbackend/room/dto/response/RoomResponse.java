package ru.example.prodbackend.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {

    @Schema(description = "Идентификатор комнаты")
    private UUID id;

    @Schema(description = "Идентификатор проекта переезда")
    private UUID projectId;

    @Schema(description = "Название комнаты", example = "Кухня")
    private String name;

    @Schema(description = "Дата создания")
    private Instant createdAt;

    @Schema(description = "Дата последнего изменения")
    private Instant updatedAt;
}

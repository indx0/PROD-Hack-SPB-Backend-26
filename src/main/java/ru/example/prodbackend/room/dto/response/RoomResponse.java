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

    @Schema(types = {"string", "null"}, description = "Ключ значка; null — нет явного выбора. Для отсутствующего или неизвестного ключа клиент использует home", example = "kitchen")
    private String iconKey;

    @Schema(description = "Дата создания")
    private Instant createdAt;

    @Schema(description = "Дата последнего изменения")
    private Instant updatedAt;
}

package ru.example.prodbackend.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор комнаты")
    private UUID id;

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор проекта переезда")
    private UUID projectId;

    @Schema(description = "Название комнаты", example = "Кухня")
    private String name;

    @Schema(types = {"string", "null"}, description = "Ключ значка; null — нет явного выбора. Для отсутствующего или неизвестного ключа клиент использует home", example = "kitchen")
    private String iconKey;

    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата создания")
    private Instant createdAt;

    @Schema(example = "2026-11-01T09:00:00Z", description = "Дата последнего изменения")
    private Instant updatedAt;
}

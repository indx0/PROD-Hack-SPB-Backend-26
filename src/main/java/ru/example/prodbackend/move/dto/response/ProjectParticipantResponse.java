package ru.example.prodbackend.move.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectParticipantResponse {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор пользователя")
    private UUID userId;
    @Schema(example = "user@example.com", description = "Адрес электронной почты пользователя")
    private String email;
    @Schema(example = "Иван", description = "Имя пользователя", types = {"string", "null"})
    private String firstName;
    @Schema(example = "Иванов", description = "Фамилия пользователя", types = {"string", "null"})
    private String lastName;
    @Schema(description = "Создатель проекта; признак не даёт дополнительных прав")
    private boolean creator;
    @Schema(example = "2026-11-01T09:00:00Z", description = "Время добавления; для создателя — время создания проекта")
    private Instant addedAt;
}

package ru.example.prodbackend.move.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectParticipantResponse {
    private UUID userId;
    private String email;
    @Schema(types = {"string", "null"})
    private String firstName;
    @Schema(types = {"string", "null"})
    private String lastName;
    @Schema(description = "Создатель проекта; признак не даёт дополнительных прав")
    private boolean creator;
    @Schema(description = "Время добавления; для создателя — время создания проекта")
    private Instant addedAt;
}

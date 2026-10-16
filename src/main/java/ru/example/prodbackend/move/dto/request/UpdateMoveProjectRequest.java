package ru.example.prodbackend.move.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMoveProjectRequest {

    @Size(min = 1, max = 255)
    @Pattern(regexp = "(?s).*\\S.*", message = "Название не должно состоять только из пробелов")
    @Schema(description = "Новое название; null сохраняет текущее значение")
    private String name;

    @Schema(description = "Новая дата переезда; null сохраняет текущее значение", example = "2026-11-02T09:00:00Z")
    private Instant moveDate;

    @Size(max = 1000)
    @Schema(description = "null сохраняет адрес, пустая строка удаляет")
    private String originAddress;

    @Size(max = 1000)
    @Schema(description = "null сохраняет адрес, пустая строка удаляет")
    private String destinationAddress;
}

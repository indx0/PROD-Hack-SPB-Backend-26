package ru.example.prodbackend.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoomRequest {

    @Size(min = 1, max = 255)
    @Pattern(regexp = "(?s).*\\S.*", message = "Название не должно состоять только из пробелов")
    @Schema(description = "Новое название; отсутствующее поле или null сохраняет текущее", example = "Спальня")
    private String name;
}
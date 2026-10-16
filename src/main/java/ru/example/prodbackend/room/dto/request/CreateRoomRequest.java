package ru.example.prodbackend.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoomRequest {

    @NotBlank(message = "Название комнаты обязательно")
    @Size(max = 255, message = "Название комнаты должно быть не длиннее 255 символов")
    @Schema(description = "Название комнаты", example = "Кухня")
    private String name;

}

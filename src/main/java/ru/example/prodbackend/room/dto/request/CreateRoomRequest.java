package ru.example.prodbackend.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    @Size(min = 1, max = 64, message = "Ключ значка должен содержать от 1 до 64 символов")
    @Pattern(regexp = "(?Us).*\\S.*", message = "Ключ значка не должен состоять только из пробелов")
    @Schema(types = {"string", "null"}, description = "Строковый ключ значка клиента: kitchen, bed, sofa, bath, book, key, heart, sun; home — запасной. Отсутствие/null — без явного выбора. Непустая строка до 64 символов", example = "kitchen")
    private String iconKey;

}

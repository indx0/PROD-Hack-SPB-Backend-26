package ru.example.prodbackend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(min = 1, max = 255, message = "email length must be between 1 and 255")
    @Pattern(regexp = ".*\\S.*", message = "email is blank")
    @Schema(example = "user@example.com", description = "Новый email; null сохраняет текущее значение")
    private String email;

    @Size(max = 255)
    @Schema(example = "Иван", description = "Имя; null сохраняет значение, пустая строка удаляет его")
    private String firstName;

    @Size(max = 255)
    @Schema(example = "Иванов", description = "Фамилия; null сохраняет значение, пустая строка удаляет его")
    private String lastName;
}

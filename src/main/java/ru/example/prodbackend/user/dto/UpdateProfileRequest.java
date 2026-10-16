package ru.example.prodbackend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import jakarta.validation.constraints.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(min = 1, max = 255, message = "email length must be between 1 and 255")
    @Pattern(regexp = ".*\\S.*", message = "email is blank")
    @Schema(description = "Новый email; null сохраняет текущее значение")
    private String email;

    @Size(max = 255)
    @Schema(description = "Имя; null сохраняет значение, пустая строка удаляет его")
    private String firstName;

    @Size(max = 255)
    @Schema(description = "Фамилия; null сохраняет значение, пустая строка удаляет его")
    private String lastName;
}

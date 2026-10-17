package ru.example.prodbackend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {
    @Schema(example = "P@ssw0rd!", description = "Текущий пароль пользователя")
    @NotBlank
    private String currentPassword;

    @Schema(example = "P@ssw0rd!", description = "Новый пароль: от 1 символа; пустая строка и строка только из пробелов запрещены")
    @NotBlank
    @Size(min = 1, message = "password length min = 1")
    private String newPassword;
}
